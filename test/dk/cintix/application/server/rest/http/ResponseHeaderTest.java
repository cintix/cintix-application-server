package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;

/**
 * Guards the response header block.
 *
 * <p>The charset used to be appended by four independent {@code if}s that each
 * matched a substring of the content type and appended unconditionally, so a
 * caller-supplied {@code charset} could end up in the header twice.</p>
 */
public class ResponseHeaderTest {

    public void runAll() {
        build_contentTypeGetsOneCharset();
        build_callerSuppliedCharsetIsNotDuplicated();
        build_callerSuppliedCharsetIsPreserved();
        build_nonTextContentTypeGetsNoCharset();
        build_payloadTooLargeHasReasonPhrase();
    }

    private static String headersOf(Response response) {
        byte[] raw = response.build();
        String text = new String(raw);
        int headerEnd = text.indexOf("\r\n\r\n");
        return headerEnd == -1 ? text : text.substring(0, headerEnd);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index != -1) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    public void build_contentTypeGetsOneCharset() {
        // Arrange
        Response response = new Response().OK().ContentType("application/json").data("{}");

        // Act
        String headers = headersOf(response);

        // Assert — exactly one charset on every text-ish content type
        TestSupport.assertEquals(1, countOccurrences(headers.toLowerCase(), "charset=utf-8"),
                "application/json should carry exactly one charset. Headers: " + headers);

        Response html = new Response().OK().ContentType("text/html").data("<p>x</p>");
        Response plain = new Response().OK().ContentType("text/plain").data("x");
        TestSupport.assertEquals(1, countOccurrences(headersOf(html).toLowerCase(), "charset=utf-8"),
                "text/html should carry exactly one charset");
        TestSupport.assertEquals(1, countOccurrences(headersOf(plain).toLowerCase(), "charset=utf-8"),
                "text/plain should carry exactly one charset");
    }

    public void build_callerSuppliedCharsetIsNotDuplicated() {
        // Arrange — a caller who set the charset themselves
        Response response = new Response().OK()
                .ContentType("application/json; charset=utf-8")
                .data("{}");

        // Act
        String headers = headersOf(response);

        // Assert — the old code appended a second charset here
        TestSupport.assertEquals(1, countOccurrences(headers.toLowerCase(), "charset"),
                "Caller-supplied charset must not be duplicated. Headers: " + headers);
    }

    public void build_callerSuppliedCharsetIsPreserved() {
        // Arrange — the caller asked for something other than UTF-8
        Response response = new Response().OK()
                .ContentType("text/html; charset=iso-8859-1")
                .data("<p>x</p>");

        // Act
        String headers = headersOf(response);

        // Assert — their choice wins; we must not override or double it
        TestSupport.assertTrue(headers.toLowerCase().contains("charset=iso-8859-1"),
                "Caller charset was dropped. Headers: " + headers);
        TestSupport.assertFalse(headers.toLowerCase().contains("charset=utf-8"),
                "Server must not add utf-8 over a caller-supplied charset. Headers: " + headers);
    }

    public void build_nonTextContentTypeGetsNoCharset() {
        // Arrange
        Response response = new Response().OK().ContentType("image/png").Content(new byte[]{1, 2, 3});

        // Act
        String headers = headersOf(response);

        // Assert — binary types must not claim a charset
        TestSupport.assertFalse(headers.toLowerCase().contains("charset"),
                "image/png should not carry a charset. Headers: " + headers);
    }

    public void build_payloadTooLargeHasReasonPhrase() {
        // Arrange
        Response response = new Response().PayloadTooLarge().data("too big");

        // Act
        String statusLine = new String(response.build()).split("\r\n")[0];

        // Assert — a bare "413 Status" is not a valid reason phrase
        TestSupport.assertEquals("HTTP/1.1 413 Payload Too Large", statusLine,
                "413 status line wrong");
    }
}
