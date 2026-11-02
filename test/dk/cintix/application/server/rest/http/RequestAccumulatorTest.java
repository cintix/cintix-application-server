package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.RequestAccumulator;
import java.nio.charset.StandardCharsets;

/**
 * Covers the decision "has the whole request arrived yet?".
 *
 * <p>Before 3.5 the server parsed whatever the first read happened to return,
 * so a request whose headers and body arrived in separate TCP segments was
 * truncated silently.</p>
 */
public class RequestAccumulatorTest {

    public void runAll() {
        headersOnlyWithoutBody_isComplete();
        declaredBodyMissing_isIncomplete();
        declaredBodySplitAcrossReads_completesOnLastSegment();
        declaredBodyShorterThanDeclared_isIncomplete();
        bodyLongerThanDeclared_isComplete();
        headersSplitAcrossReads_isIncompleteUntilTerminated();
        bareLineFeedTerminator_isAccepted();
        chunkedBody_isIncompleteUntilTerminatingChunk();
        chunkedBody_splitInsideChunk_staysIncomplete();
        chunkedBody_toByteArray_returnsDecodedBody();
        chunkedBody_withExtensionsAndTrailers_isComplete();
        chunkedBody_invalidChunkSize_isMalformed();
        nonChunkedTransferEncoding_completesAtHeaders();
        expectContinue_requestedOnlyWhileBodyOutstanding();
        expectContinue_notRequestedWhenBodyAlreadyArrived();
        expectContinue_silentOnceMarkedSent();
        parserContentLengthGuard_matchesAccumulator();
        indexOfHeaderEnd_findsBothTerminators();
    }

    private static void append(RequestAccumulator accumulator, String text) {
        accumulator.append(text.getBytes(StandardCharsets.UTF_8));
    }

    public void headersOnlyWithoutBody_isComplete() {
        // Arrange — a GET has no body and no Content-Length
        RequestAccumulator accumulator = new RequestAccumulator();

        // Act
        append(accumulator, "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");

        // Assert
        TestSupport.assertTrue(accumulator.isComplete(), "Bodiless request must be complete at its headers");
    }

    public void declaredBodyMissing_isIncomplete() {
        // Arrange — headers promise 11 bytes that have not arrived
        RequestAccumulator accumulator = new RequestAccumulator();

        // Act
        append(accumulator, "POST /submit HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 11\r\n\r\n");

        // Assert
        TestSupport.assertFalse(accumulator.isComplete(),
                "Must not report complete before the promised body arrives");
    }

    public void declaredBodySplitAcrossReads_completesOnLastSegment() {
        // Arrange — the exact shape that used to truncate: headers, then body
        String headers = "POST /submit HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 11\r\n\r\n";
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, headers);

        // Act — body arrives in two more segments
        append(accumulator, "name=alice");
        boolean afterFirstHalf = accumulator.isComplete();
        append(accumulator, "&x=1");
        boolean afterSecondHalf = accumulator.isComplete();

        // Assert
        TestSupport.assertFalse(afterFirstHalf, "Half a body is not a whole request");
        TestSupport.assertTrue(afterSecondHalf, "Request must complete once Content-Length is satisfied");
        TestSupport.assertEquals(headers.length() + "name=alice".length() + "&x=1".length(),
                accumulator.size(), "Buffered byte count wrong");
    }

    public void declaredBodyShorterThanDeclared_isIncomplete() {
        // Arrange
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nContent-Length: 5\r\n\r\nabc");

        // Act / Assert — one byte short
        TestSupport.assertFalse(accumulator.isComplete(), "Short body must stay incomplete");
    }

    public void bodyLongerThanDeclared_isComplete() {
        // Arrange — a client that sent more than it declared
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nContent-Length: 3\r\n\r\nabcdef");

        // Act / Assert — never block a request that has already over-delivered
        TestSupport.assertTrue(accumulator.isComplete(), "Over-delivered body must not stall");
    }

    public void headersSplitAcrossReads_isIncompleteUntilTerminated() {
        // Arrange — a request line and half the headers
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nHost: 127.0.0.1\r\n");

        // Act
        boolean beforeTerminator = accumulator.isComplete();
        append(accumulator, "Content-Length: 0\r\n\r\n");
        boolean afterTerminator = accumulator.isComplete();

        // Assert
        TestSupport.assertFalse(beforeTerminator, "Unterminated headers must not parse");
        TestSupport.assertTrue(afterTerminator, "Zero-length body completes at the terminator");
    }

    public void bareLineFeedTerminator_isAccepted() {
        // Arrange — parseRequest also understands LF LF, so the accumulator must
        RequestAccumulator accumulator = new RequestAccumulator();

        // Act
        append(accumulator, "GET /hello HTTP/1.1\nHost: 127.0.0.1\n\n");

        // Assert
        TestSupport.assertTrue(accumulator.isComplete(), "LF LF terminator must be recognised");
    }

    public void chunkedBody_isIncompleteUntilTerminatingChunk() {
        // Arrange — a chunked body has no knowable length up front
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n");

        // Act — the old accumulator called this complete at the headers and
        // then handed an empty body to the endpoint.
        boolean headersOnly = accumulator.isComplete();
        append(accumulator, "5\r\nhello\r\n");
        boolean afterFirstChunk = accumulator.isComplete();
        append(accumulator, "0\r\n\r\n");
        boolean afterTerminator = accumulator.isComplete();

        // Assert
        TestSupport.assertFalse(headersOnly, "Chunked request must not complete at the headers");
        TestSupport.assertFalse(afterFirstChunk, "A chunked body without its terminating chunk is incomplete");
        TestSupport.assertTrue(afterTerminator, "Zero-length chunk plus its blank line completes the request");
    }

    public void chunkedBody_splitInsideChunk_staysIncomplete() {
        // Arrange — segments split mid-payload and mid-terminator
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "a\r\n01234");

        // Act / Assert — half the chunk data, no line break yet
        TestSupport.assertFalse(accumulator.isComplete(), "Incomplete chunk data must not count as complete");

        append(accumulator, "56789\r\n0\r\n");
        TestSupport.assertFalse(accumulator.isComplete(),
                "The blank line ending the trailer section is still missing");

        append(accumulator, "\r\n");
        TestSupport.assertTrue(accumulator.isComplete(), "Request completes when the trailer ends");
    }

    public void chunkedBody_toByteArray_returnsDecodedBody() {
        // Arrange — the server must see the same shape as a Content-Length body
        RequestAccumulator accumulator = new RequestAccumulator();
        String headers = "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n";
        append(accumulator, headers
                + "5\r\nhello\r\n"
                + "6\r\n world\r\n"
                + "0\r\n\r\n");

        // Act
        boolean complete = accumulator.isComplete();
        String request = new String(accumulator.toByteArray(), StandardCharsets.UTF_8);

        // Assert — chunk framing gone, payload intact
        TestSupport.assertTrue(complete, "Chunked request should be complete");
        TestSupport.assertEquals(headers + "hello world", request,
                "toByteArray must return headers followed by the decoded body");
    }

    public void chunkedBody_withExtensionsAndTrailers_isComplete() {
        // Arrange — chunk extensions and a trailer are legal and must be parsed
        RequestAccumulator accumulator = new RequestAccumulator();
        String headers = "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n";
        append(accumulator, headers
                + "3;name=value\r\nabc\r\n"
                + "0\r\nX-Trailer: 1\r\n\r\n");

        // Act
        boolean complete = accumulator.isComplete();
        String request = new String(accumulator.toByteArray(), StandardCharsets.UTF_8);

        // Assert — the extension and the trailer are framing, not payload
        TestSupport.assertTrue(complete, "Extensions and trailers must not stall the scan");
        TestSupport.assertEquals(headers + "abc", request, "Framing leaked into the decoded body");
    }

    public void chunkedBody_invalidChunkSize_isMalformed() {
        // Arrange — "zz" is not hexadecimal
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nabc\r\n");

        // Act / Assert — reported so the server can answer 400 instead of hanging
        TestSupport.assertTrue(accumulator.isMalformed(),
                "An unreadable chunk size must be reported as malformed");
    }

    public void nonChunkedTransferEncoding_completesAtHeaders() {
        // Arrange — a coding we cannot frame (gzip) has no knowable length
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nTransfer-Encoding: gzip\r\n\r\n");

        // Act / Assert — keeps the old behaviour rather than stalling forever
        TestSupport.assertTrue(accumulator.isComplete(),
                "A non-chunked transfer coding must not stall waiting for Content-Length");
        TestSupport.assertFalse(accumulator.isChunked(), "gzip is not chunked framing");
    }

    public void expectContinue_requestedOnlyWhileBodyOutstanding() {
        // Arrange — the client is holding a 10-byte body back
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nExpect: 100-continue\r\nContent-Length: 10\r\n\r\n");

        // Act
        boolean beforeBody = accumulator.shouldSendContinue();

        append(accumulator, "name=alice");

        // Assert
        TestSupport.assertTrue(beforeBody, "The interim response is owed while the body is outstanding");
        TestSupport.assertFalse(accumulator.shouldSendContinue(),
                "No interim response once the whole request has arrived");
    }

    public void expectContinue_notRequestedWhenBodyAlreadyArrived() {
        // Arrange — a client that sent the body eagerly, in the same segment
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nExpect: 100-continue\r\nContent-Length: 3\r\n\r\nabc");

        // Act / Assert — there is nothing to continue into
        TestSupport.assertFalse(accumulator.shouldSendContinue(),
                "A complete request must not be answered with 100 Continue");
    }

    public void expectContinue_silentOnceMarkedSent() {
        // Arrange — the interim response is written at most once per request
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nExpect: 100-continue\r\nContent-Length: 11\r\n\r\n");

        // Act
        boolean first = accumulator.shouldSendContinue();
        accumulator.markContinueSent();
        boolean second = accumulator.shouldSendContinue();

        // Assert
        TestSupport.assertTrue(first, "Interim response expected before the body arrives");
        TestSupport.assertFalse(second, "The interim response must not be sent twice");
    }

    public void parserContentLengthGuard_matchesAccumulator() {
        // Arrange — a zero-length body must not be treated as "no body"
        RequestAccumulator accumulator = new RequestAccumulator();

        // Act
        append(accumulator, "POST /submit HTTP/1.1\r\nContent-Length: 0\r\n\r\n");

        // Assert
        TestSupport.assertTrue(accumulator.isComplete(), "Content-Length: 0 is a complete request");
    }

    public void indexOfHeaderEnd_findsBothTerminators() {
        // Arrange
        byte[] crlf = "GET / HTTP/1.1\r\nHost: h\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);
        byte[] lf = "GET / HTTP/1.1\nHost: h\n\nbody".getBytes(StandardCharsets.UTF_8);
        byte[] unterminated = "GET / HTTP/1.1\r\nHost: h\r\n".getBytes(StandardCharsets.UTF_8);

        // Act
        int crlfEnd = RequestAccumulator.indexOfHeaderEnd(crlf);
        int lfEnd = RequestAccumulator.indexOfHeaderEnd(lf);
        int unterminatedEnd = RequestAccumulator.indexOfHeaderEnd(unterminated);

        // Assert — the offset must point at the first body byte
        TestSupport.assertEquals(crlf.length - "body".length(), crlfEnd, "CRLF CRLF offset wrong");
        TestSupport.assertEquals(lf.length - "body".length(), lfEnd, "LF LF offset wrong");
        TestSupport.assertEquals(-1, unterminatedEnd, "Unterminated headers should report -1");
        TestSupport.assertEquals("body", new String(crlf, crlfEnd, 4, StandardCharsets.UTF_8),
                "Offset does not point at the body");
        TestSupport.assertEquals("body", new String(lf, lfEnd, 4, StandardCharsets.UTF_8),
                "Offset does not point at the body");
    }
}
