package dk.cintix.application.server.rest.http.utils;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.HttpUtil;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpUtilTest {

    public void runAll() {
        parseQueryStrings_extractsPathAndParameters();
        parseQueryStrings_handlesEmptyAssignedValue();
        parseQueryStrings_decodesUrlEncodedValues();
        parseQueryStrings_plusIsDecodedAsSpace();
        parseQueryStrings_keepsValuelessKey();
        parseHeaderKeys_keepsColonInsideHeaderValue();
        parsePostFields_handlesEmptyAssignedValue();
        parsePostFields_singleLineBodyYieldsFields();
        parsePostFields_multiLineBodyYieldsEveryField();
        parsePostFields_multiLineBodyKeepsRawIntact();
        parsePostFields_decodesUrlEncodedValues();
        parsePostFields_rawIsNotDecoded();
        urlDecode_malformedEncodingReturnsRawValue();
        parseContentLength_readsDeclaredLength();
        parseContentLength_isCaseInsensitive();
        parseContentLength_returnsMinusOneWhenAbsentOrInvalid();
        buildContextPath_joinsSegmentsWithoutTrailingSlash();
    }

    /**
     * Runs the real header-then-body pipeline over a raw request, exactly as
     * {@code RestHttpServer.parseRequest} does.
     */
    private static Map<String, String> parseRequestLike(String rawRequest) {
        String[] lines = rawRequest.split("\n");
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        int linesProcessed = HttpUtil.parseHeaderKeys(lines, headers, 0);
        HttpUtil.parsePostFields(linesProcessed, lines, fields);
        return fields;
    }

    public void parseQueryStrings_extractsPathAndParameters() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();

        // Act
        String path = HttpUtil.parseQueryStrings("/users/list?page=2&sort=asc", query);

        // Assert
        TestSupport.assertEquals("/users/list", path, "Path extraction failed");
        TestSupport.assertEquals("2", query.get("page"), "page parse failed");
        TestSupport.assertEquals("asc", query.get("sort"), "sort parse failed");
    }

    public void parseQueryStrings_handlesEmptyAssignedValue() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();

        // Act
        HttpUtil.parseQueryStrings("/search?q=", query);

        // Assert
        TestSupport.assertTrue(query.containsKey("q"), "Missing q key");
        TestSupport.assertEquals("", query.get("q"), "Empty query value parse failed");
    }

    public void parseHeaderKeys_keepsColonInsideHeaderValue() {
        // Arrange
        String[] lines = new String[]{"GET / HTTP/1.1", "Host: localhost:8080", "X-Test: alpha:beta", ""};
        Map<String, String> headers = new LinkedHashMap<>();

        // Act
        int processed = HttpUtil.parseHeaderKeys(lines, headers, 0);

        // Assert
        TestSupport.assertTrue(processed > 0, "No header lines processed");
        TestSupport.assertEquals("localhost:8080", headers.get("HOST"), "Host header truncated");
        TestSupport.assertEquals("alpha:beta", headers.get("X-TEST"), "Custom header truncated");
    }

    public void parsePostFields_handlesEmptyAssignedValue() {
        // Arrange
        String[] lines = new String[]{"", "name=&mode=on", ""};
        Map<String, String> fields = new LinkedHashMap<>();

        // Act
        HttpUtil.parsePostFields(1, lines, fields);

        // Assert
        TestSupport.assertEquals("", fields.get("name"), "Empty post value parse failed");
        TestSupport.assertEquals("on", fields.get("mode"), "Post value parse failed");
    }

    public void parseQueryStrings_decodesUrlEncodedValues() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();

        // Act
        HttpUtil.parseQueryStrings("/search?q=s%20m%20E%203%20d%40&city=%C3%A6r%C3%B8", query);

        // Assert
        TestSupport.assertEquals("s m E 3 d@", query.get("q"), "Percent-encoding not decoded");
        TestSupport.assertEquals("ærø", query.get("city"), "UTF-8 percent-encoding not decoded");
    }

    public void parseQueryStrings_plusIsDecodedAsSpace() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();

        // Act
        HttpUtil.parseQueryStrings("/search?q=hello+world", query);

        // Assert
        TestSupport.assertEquals("hello world", query.get("q"), "Plus not decoded as space");
    }

    public void parseQueryStrings_keepsValuelessKey() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();

        // Act
        HttpUtil.parseQueryStrings("/?jsd", query);

        // Assert
        TestSupport.assertTrue(query.containsKey("jsd"),
                "Valueless key dropped — the /?jsd documentation switch depends on it");
        TestSupport.assertEquals("", query.get("jsd"), "Valueless key should map to empty string");
    }

    public void parsePostFields_singleLineBodyYieldsFields() {
        // Arrange — headers and a one-line body, as a client actually sends it
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "\r\n"
                + "name=alice&mode=on";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert
        TestSupport.assertEquals("alice", fields.get("name"), "Single-line body lost its fields");
        TestSupport.assertEquals("on", fields.get("mode"), "Second field of single-line body lost");
    }

    public void parsePostFields_multiLineBodyYieldsEveryField() {
        // Arrange — a pretty-printed form body spanning three lines
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "\r\n"
                + "name=alice\r\n"
                + "mode=on\r\n"
                + "city=roskilde";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert — every line contributes, not just the first
        TestSupport.assertEquals("alice", fields.get("name"), "First body line lost");
        TestSupport.assertEquals("on", fields.get("mode"), "Second body line was dropped");
        TestSupport.assertEquals("roskilde", fields.get("city"), "Third body line was dropped");
    }

    public void parsePostFields_multiLineBodyKeepsRawIntact() {
        // Arrange
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "\r\n"
                + "name=alice\r\n"
                + "mode=on";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);
        String raw = fields.get("!RAW");

        // Assert — !RAW reproduces the body, newlines included
        TestSupport.assertTrue(raw != null, "!RAW was never populated");
        TestSupport.assertEquals("name=alice\r\nmode=on", raw, "!RAW mangled the multi-line body");
    }

    public void parsePostFields_decodesUrlEncodedValues() {
        // Arrange
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "\r\n"
                + "name=Alice+Smith&note=a%3Db%26c&city=%C3%A6r%C3%B8";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert
        TestSupport.assertEquals("Alice Smith", fields.get("name"), "Plus not decoded in form field");
        TestSupport.assertEquals("a=b&c", fields.get("note"),
                "Encoded & and = must survive as data, not split the field");
        TestSupport.assertEquals("ærø", fields.get("city"), "UTF-8 form value not decoded");
    }

    public void parsePostFields_rawIsNotDecoded() {
        // Arrange — a JSON body full of characters that percent-decoding would eat
        String body = "{\"formula\":\"a+b=c\",\"pct\":\"100%\"}";
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/json\r\n"
                + "\r\n"
                + body;

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert
        TestSupport.assertEquals(body, fields.get("!RAW"),
                "!RAW must stay byte-for-byte; decoding it would corrupt JSON payloads");
    }

    public void urlDecode_malformedEncodingReturnsRawValue() {
        // Arrange — a bare % and a non-hex escape are both illegal
        // Act / Assert — neither may throw, both must come back unchanged
        TestSupport.assertEquals("100%", HttpUtil.urlDecode("100%"), "Bare % must not throw");
        TestSupport.assertEquals("%ZZ", HttpUtil.urlDecode("%ZZ"), "Bad escape must not throw");
        TestSupport.assertEquals("plain", HttpUtil.urlDecode("plain"), "Plain value changed");
    }

    public void parseContentLength_readsDeclaredLength() {
        // Arrange
        String headers = "POST /submit HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 42\r\n\r\n";

        // Act
        long length = HttpUtil.parseContentLength(headers);

        // Assert
        TestSupport.assertEquals(42L, length, "Content-Length not read");
    }

    public void parseContentLength_isCaseInsensitive() {
        // Arrange — header names are case-insensitive on the wire
        String headers = "POST /submit HTTP/1.1\r\ncontent-length: 7\r\n\r\n";

        // Act
        long length = HttpUtil.parseContentLength(headers);

        // Assert
        TestSupport.assertEquals(7L, length, "Content-Length must match case-insensitively");
    }

    public void parseContentLength_returnsMinusOneWhenAbsentOrInvalid() {
        // Arrange
        String absent = "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n";
        String notANumber = "POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n";
        String negative = "POST / HTTP/1.1\r\nContent-Length: -5\r\n\r\n";

        // Act / Assert — all three mean "no usable declared length"
        TestSupport.assertEquals(-1L, HttpUtil.parseContentLength(absent), "Absent header should be -1");
        TestSupport.assertEquals(-1L, HttpUtil.parseContentLength(notANumber), "Non-numeric should be -1");
        TestSupport.assertEquals(-1L, HttpUtil.parseContentLength(negative), "Negative should be -1");
    }

    public void buildContextPath_joinsSegmentsWithoutTrailingSlash() {
        // Arrange
        String[] segments = new String[]{"api", "v1", "users", "123"};

        // Act
        String path = HttpUtil.buildContextPath(segments);

        // Assert
        TestSupport.assertEquals("api/v1/users", path, "Context path build failed");
    }
}
