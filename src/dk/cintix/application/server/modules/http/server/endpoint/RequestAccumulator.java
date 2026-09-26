package dk.cintix.application.server.modules.http.server.endpoint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Buffers the bytes of one in-flight request and decides when the whole request
 * has arrived.
 *
 * <p>A request is complete once its header block is terminated and at least
 * {@code Content-Length} body bytes have been buffered. A request that carries
 * no usable {@code Content-Length} — and any request declaring
 * {@code Transfer-Encoding}, whose length is not knowable up front — counts as
 * complete at the end of its headers. That is the pre-3.5 behaviour for every
 * bodiless request, so GET and friends are unaffected.</p>
 *
 * <p>The header block is decoded at most once, when its terminator is first
 * found. After that {@link #isComplete()} is a size comparison, so a multi-
 * megabyte body arriving in 2 KB segments costs one scan rather than one per
 * segment.</p>
 *
 * @author cix
 */
public class RequestAccumulator {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /** Offset just past the header terminator, or -1 while still searching. */
    private int headerEnd = -1;

    /** Declared body length once the headers are known; -1 while unknown. */
    private long expectedBodyLength = -1;

    public void append(byte[] bytes) {
        buffer.write(bytes, 0, bytes.length);
    }

    public int size() {
        return buffer.size();
    }

    public boolean isEmpty() {
        return buffer.size() == 0;
    }

    /**
     * @return {@code true} once the buffered bytes hold a whole request.
     */
    public boolean isComplete() {
        if (headerEnd == -1) {
            byte[] raw = buffer.toByteArray();
            headerEnd = indexOfHeaderEnd(raw);
            if (headerEnd == -1) {
                return false;
            }
            String headerBlock = new String(raw, 0, headerEnd, StandardCharsets.UTF_8);
            expectedBodyLength = headerBlock.toLowerCase().contains("transfer-encoding")
                    ? 0
                    : HttpUtil.parseContentLength(headerBlock);
            if (expectedBodyLength < 0) {
                expectedBodyLength = 0;
            }
        }
        return (buffer.size() - headerEnd) >= expectedBodyLength;
    }

    public byte[] toByteArray() {
        return buffer.toByteArray();
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
        for (int index = 1; index < raw.length; index++) {
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
