package dk.cintix.application.server.modules.http.server.endpoint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Buffers the bytes of one in-flight request and decides when the whole request
 * has arrived.
 *
 * <p>A request is complete once its header block is terminated and its body,
 * framed either by {@code Content-Length} or by chunked transfer coding, has
 * fully arrived. A bodyless request — no {@code Content-Length}, no
 * {@code Transfer-Encoding} — counts as complete at the end of its headers, so
 * GET and friends are unaffected.</p>
 *
 * <p>The header block is decoded at most once, when its terminator is first
 * found. After that {@link #isComplete()} is a size comparison, so a multi-
 * megabyte body arriving in 2 KB segments costs one scan rather than one per
 * segment. Chunk framing is likewise parsed incrementally: the scan resumes
 * from the last complete chunk, so each byte is examined a bounded number of
 * times no matter how many segments the body is split into.</p>
 *
 * <p>When the body is chunked, {@link #toByteArray()} returns the request with
 * the body already de-chunked — headers followed by the payload — so the rest
 * of the server sees exactly the same shape it sees for a
 * {@code Content-Length} request. Nothing downstream has to know the body
 * arrived as chunks.</p>
 *
 * @author cix
 */
public class RequestAccumulator {

    private byte[] buffer = new byte[2048];
    private int size;

    /** Offset just past the header terminator, or -1 while still searching. */
    private int headerEnd = -1;

    /** Declared body length once the headers are known; -1 while unknown. */
    private long expectedBodyLength = -1;

    /** True when the body is framed with the chunked transfer coding. */
    private boolean chunked;

    /** Absolute offset of the next chunk to parse; -1 until the headers land. */
    private int chunkOffset = -1;

    /** True once the terminating zero-length chunk and its trailer were seen. */
    private boolean chunkedComplete;

    /** True when the chunk framing is not valid hex — answered with 400. */
    private boolean malformed;

    /** True when the client asked for a "100 Continue" interim response. */
    private boolean expectsContinue;

    /** True once that interim response has been written to the socket. */
    private boolean continueSent;

    public void append(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        ensureCapacity(size + bytes.length);
        System.arraycopy(bytes, 0, buffer, size, bytes.length);
        size += bytes.length;
    }

    private void ensureCapacity(int required) {
        if (required <= buffer.length) {
            return;
        }
        int capacity = buffer.length;
        while (capacity < required) {
            capacity *= 2;
        }
        buffer = Arrays.copyOf(buffer, capacity);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return {@code true} once the buffered bytes hold a whole request.
     */
    public boolean isComplete() {
        if (!scanHeaders()) {
            return false;
        }
        if (malformed) {
            // Stop reading: handleRead answers 400 rather than wait for a body
            // delimitation that can never be valid.
            return true;
        }
        if (chunked) {
            return scanChunkedBody();
        }
        return (long) (size - headerEnd) >= expectedBodyLength;
    }

    /**
     * @return {@code true} when the chunk framing cannot be parsed — an
     *         unreadable chunk size, or chunk data not followed by a line
     *         break. The request must be answered with 400.
     */
    public boolean isMalformed() {
        if (!scanHeaders()) {
            return false;
        }
        if (chunked && !malformed) {
            scanChunkedBody();
        }
        return malformed;
    }

    /**
     * @return {@code true} when the body is framed with chunked transfer coding
     *         rather than a declared length.
     */
    public boolean isChunked() {
        scanHeaders();
        return chunked;
    }

    /**
     * Decides whether the server owes the client an interim
     * {@code 100 Continue}.
     *
     * <p>Clients send {@code Expect: 100-continue} with a large body and then
     * wait — curl for up to a second — before sending it anyway. Answering only
     * when a body is still outstanding keeps the exchange correct for a client
     * that omitted the body or sent it eagerly.</p>
     *
     * @return {@code true} when the interim response should be written now
     */
    public boolean shouldSendContinue() {
        if (continueSent || !scanHeaders() || !expectsContinue) {
            return false;
        }
        if (isComplete()) {
            return false;
        }
        // No body pending means nothing to continue into.
        return chunked || expectedBodyLength > 0;
    }

    /** Records that the interim {@code 100 Continue} response was written. */
    public void markContinueSent() {
        continueSent = true;
    }

    public byte[] toByteArray() {
        if (!chunked || !chunkedComplete) {
            return Arrays.copyOf(buffer, size);
        }
        byte[] body = dechunkedBody();
        byte[] request = new byte[headerEnd + body.length];
        System.arraycopy(buffer, 0, request, 0, headerEnd);
        System.arraycopy(body, 0, request, headerEnd, body.length);
        return request;
    }

    // --- Header framing ----------------------------------------------------

    private boolean scanHeaders() {
        if (headerEnd != -1) {
            return true;
        }
        int end = indexOfHeaderEnd(buffer, 0, size);
        if (end == -1) {
            return false;
        }
        headerEnd = end;
        String headerBlock = new String(buffer, 0, end, StandardCharsets.UTF_8);

        String transferEncoding = HttpUtil.parseHeaderValue(headerBlock, "Transfer-Encoding");
        chunked = transferEncoding != null
                && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked");
        if (transferEncoding != null) {
            // A coding we cannot frame (gzip, say) has no knowable length, so
            // keep the historical behaviour: complete at the headers rather
            // than stall forever. Only "chunked" gets the streaming path.
            expectedBodyLength = 0;
        } else {
            expectedBodyLength = HttpUtil.parseContentLength(headerBlock);
            if (expectedBodyLength < 0) {
                expectedBodyLength = 0;
            }
        }

        String expect = HttpUtil.parseHeaderValue(headerBlock, "Expect");
        expectsContinue = expect != null && expect.toLowerCase(Locale.ROOT).contains("100-continue");

        chunkOffset = headerEnd;
        return true;
    }

    // --- Chunked framing ---------------------------------------------------

    /**
     * Walks chunk headers lazily, stopping as soon as one is incomplete.
     *
     * @return {@code true} when the terminating chunk and its trailer have
     *         arrived
     */
    private boolean scanChunkedBody() {
        if (chunkedComplete) {
            return true;
        }
        if (chunkOffset < headerEnd) {
            chunkOffset = headerEnd;
        }
        while (true) {
            int lineEnd = indexOfNewline(buffer, chunkOffset, size);
            if (lineEnd == -1) {
                return false;
            }

            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeLine(chunkOffset, lineEnd), 16);
            } catch (NumberFormatException e) {
                malformed = true;
                return true;
            }
            if (chunkSize < 0) {
                malformed = true;
                return true;
            }

            int dataStart = lineEnd + 1;
            if (chunkSize == 0) {
                int trailerEnd = endOfTrailerSection(dataStart);
                if (trailerEnd == -1) {
                    return false;
                }
                chunkedComplete = true;
                chunkOffset = trailerEnd;
                return true;
            }

            long dataEnd = (long) dataStart + chunkSize;
            if (dataEnd > size) {
                return false;
            }
            int next = (int) dataEnd;
            if (next >= size) {
                // The chunk data is here but its line break is not.
                return false;
            }
            if (buffer[next] == '\r') {
                next++;
                if (next >= size) {
                    return false;
                }
            }
            if (buffer[next] != '\n') {
                malformed = true;
                return true;
            }
            chunkOffset = next + 1;
        }
    }

    /**
     * @return the chunk-size line without its line break or extensions
     */
    private String chunkSizeLine(int from, int lineEnd) {
        String line = new String(buffer, from, lineEnd - from, StandardCharsets.US_ASCII).trim();
        int extension = line.indexOf(';');
        if (extension != -1) {
            line = line.substring(0, extension).trim();
        }
        return line;
    }

    /**
     * Skips the (usually empty) trailer section that follows the terminating
     * chunk.
     *
     * @param from the first byte after the terminating chunk's line break
     * @return the offset just past the blank line ending the trailer, or -1
     *         while it has not arrived yet
     */
    private int endOfTrailerSection(int from) {
        int index = from;
        while (index < size) {
            if (buffer[index] == '\n') {
                return index + 1;
            }
            if (buffer[index] == '\r') {
                if (index + 1 >= size) {
                    return -1;
                }
                if (buffer[index + 1] == '\n') {
                    return index + 2;
                }
            }
            int newline = indexOfNewline(buffer, index, size);
            if (newline == -1) {
                return -1;
            }
            index = newline + 1;
        }
        return -1;
    }

    /**
     * Reassembles the payload from the chunk framing.
     *
     * <p>Only called once {@link #scanChunkedBody()} reported the body
     * complete, so the walked offsets are known to be valid.</p>
     */
    private byte[] dechunkedBody() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int cursor = headerEnd;
        while (cursor < size) {
            int lineEnd = indexOfNewline(buffer, cursor, size);
            if (lineEnd == -1) {
                break;
            }
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeLine(cursor, lineEnd), 16);
            } catch (NumberFormatException e) {
                break;
            }
            cursor = lineEnd + 1;
            if (chunkSize <= 0) {
                break;
            }
            int available = Math.min(chunkSize, size - cursor);
            body.write(buffer, cursor, available);
            cursor += available;
            if (available < chunkSize) {
                break;
            }
            if (cursor < size && buffer[cursor] == '\r') {
                cursor++;
            }
            if (cursor < size && buffer[cursor] == '\n') {
                cursor++;
            }
        }
        return body.toByteArray();
    }

    // --- Scanning helpers --------------------------------------------------

    private static int indexOfNewline(byte[] raw, int from, int to) {
        for (int index = Math.max(from, 0); index < to; index++) {
            if (raw[index] == '\n') {
                return index;
            }
        }
        return -1;
    }

    /**
     * Locates the end of the header block, accepting both the {@code CRLF CRLF}
     * terminator required by RFC 7230 and a bare {@code LF LF}, which
     * {@code parseRequest} also understands.
     *
     * @return the offset of the first body byte, or -1 when the headers are
     *         not terminated yet
     */
    public static int indexOfHeaderEnd(byte[] raw) {
        return indexOfHeaderEnd(raw, 0, raw == null ? 0 : raw.length);
    }

    private static int indexOfHeaderEnd(byte[] raw, int from, int to) {
        for (int index = Math.max(from, 0) + 1; index < to; index++) {
            if (raw[index] != '\n') {
                continue;
            }
            if (raw[index - 1] == '\n') {
                return index + 1;
            }
            if (index >= 3 && raw[index - 1] == '\r'
                    && raw[index - 2] == '\n' && raw[index - 3] == '\r') {
                return index + 1;
            }
        }
        return -1;
    }

}
