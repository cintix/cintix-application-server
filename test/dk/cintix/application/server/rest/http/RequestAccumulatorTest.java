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
        transferEncoding_isCompleteAtHeaders();
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

    public void transferEncoding_isCompleteAtHeaders() {
        // Arrange — a chunked body has no knowable length up front
        RequestAccumulator accumulator = new RequestAccumulator();
        append(accumulator, "POST /submit HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n");

        // Act — must not wait forever for a Content-Length that never comes
        TestSupport.assertTrue(accumulator.isComplete(),
                "Transfer-Encoding request must not stall waiting for Content-Length");
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
