package dk.cintix.application.server.rest.http.utils;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.HttpUtil;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpUtilTest {

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    public void runAll() {
        parseQueryStrings_extractsPathAndParameters();
        parseQueryStrings_handlesEmptyAssignedValue();
        parseQueryStrings_decodesUrlEncodedValues();
        parseQueryStrings_plusIsDecodedAsSpace();
        parseQueryStrings_keepsValuelessKey();
        parseQueryStrings_multiValueKeepsEveryValue();
        parseHeaderKeys_keepsColonInsideHeaderValue();
        parsePostFields_handlesEmptyAssignedValue();
        parsePostFields_singleLineBodyYieldsFields();
        parsePostFields_multiLineBodyYieldsEveryField();
        parsePostFields_multiLineBodyKeepsRawIntact();
        parsePostFields_decodesUrlEncodedValues();
        parsePostFields_rawIsNotDecoded();
        parsePostFields_multiValueKeepsEveryValue();
        parsePostFields_jsonBodyIsNotMinedForFields();
        parsePostFields_formContentTypeWithCharsetIsMined();
        restHttpRequest_multiValueAccessorsReturnEveryValue();
        restHttpRequest_singleValueConstructorDerivesMultiValueView();
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
        return parseRequestLike(rawRequest, null);
    }

    private static Map<String, String> parseRequestLike(String rawRequest, Map<String, List<String>> values) {
        String[] lines = rawRequest.split("\n");
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        int linesProcessed = HttpUtil.parseHeaderKeys(lines, headers, 0);
        HttpUtil.parsePostFields(linesProcessed, lines, headers.get("CONTENT-TYPE"), fields, values);
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

    public void parseQueryStrings_multiValueKeepsEveryValue() {
        // Arrange — a repeated key, which the single-value map collapses
        Map<String, String> query = new LinkedHashMap<>();
        Map<String, List<String>> values = new LinkedHashMap<>();

        // Act
        HttpUtil.parseQueryStrings("/items?tag=a&tag=b&page=2", query, values);

        // Assert — last-wins behaviour is preserved for existing callers
        TestSupport.assertEquals("b", query.get("tag"),
                "Single-value map must keep its last-wins behaviour");
        // …while the multi-value view keeps every value, in order
        TestSupport.assertEquals(Arrays.asList("a", "b"), values.get("tag"),
                "Repeated query values were collapsed");
        TestSupport.assertEquals(Arrays.asList("2"), values.get("page"),
                "Single query value missing from multi-value view");
    }

    public void parsePostFields_multiValueKeepsEveryValue() {
        // Arrange — the same field submitted twice in one form body
        Map<String, List<String>> values = new LinkedHashMap<>();
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "\r\n"
                + "tag=a&tag=b";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest, values);

        // Assert
        TestSupport.assertEquals("b", fields.get("tag"), "Single-value map must stay last-wins");
        TestSupport.assertEquals(Arrays.asList("a", "b"), values.get("tag"),
                "Repeated form values were collapsed");
    }

    public void parsePostFields_jsonBodyIsNotMinedForFields() {
        // Arrange — a JSON body containing '=' is not a form
        String body = "{\"a\":\"x=1\"}";
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/json\r\n"
                + "\r\n"
                + body;

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert — no bogus field named 'a'; the body is available as !RAW
        TestSupport.assertFalse(fields.containsKey("a"),
                "A JSON body must not be mined for form fields");
        TestSupport.assertEquals(body, fields.get("!RAW"), "!RAW must still carry the body");
    }

    public void parsePostFields_formContentTypeWithCharsetIsMined() {
        // Arrange — browsers append a charset parameter to the form type
        String rawRequest = "POST /submit HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n"
                + "\r\n"
                + "name=alice";

        // Act
        Map<String, String> fields = parseRequestLike(rawRequest);

        // Assert
        TestSupport.assertEquals("alice", fields.get("name"),
                "A charset parameter must not stop form parsing");
    }

    public void restHttpRequest_multiValueAccessorsReturnEveryValue() {
        // Arrange
        Map<String, String> query = new LinkedHashMap<>();
        Map<String, List<String>> queryValues = new LinkedHashMap<>();
        Map<String, String> post = new LinkedHashMap<>();
        Map<String, List<String>> postValues = new LinkedHashMap<>();
        HttpUtil.parseQueryStrings("/items?tag=a&tag=b", query, queryValues);
        HttpUtil.parsePostFields(0, new String[]{"x=1&x=2"}, FORM_CONTENT_TYPE, post, postValues);

        RestHttpRequest request = new RestHttpRequest(new LinkedHashMap<String, String>(),
                query, post, queryValues, postValues, null, "POST", "/items", "x=1&x=2");

        // Act / Assert
        TestSupport.assertEquals(Arrays.asList("a", "b"), request.getQueryStringValues("tag"),
                "getQueryStringValues must return every query value");
        TestSupport.assertEquals(Arrays.asList("1", "2"), request.getPostValues("x"),
                "getPostValues must return every form value");
        TestSupport.assertTrue(request.getQueryStringValues("missing").isEmpty(),
                "An absent query key must yield an empty list, not null");
        TestSupport.assertTrue(request.getPostValues("missing").isEmpty(),
                "An absent form key must yield an empty list, not null");
    }

    public void restHttpRequest_singleValueConstructorDerivesMultiValueView() {
        // Arrange — old constructor, as used by tests and third-party code
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "2");
        RestHttpRequest request = new RestHttpRequest(new LinkedHashMap<String, String>(),
                query, new LinkedHashMap<String, String>(), null, "GET", "/items", "");

        // Act / Assert — a single value is still visible through the new API
        TestSupport.assertEquals(Arrays.asList("2"), request.getQueryStringValues("page"),
                "Legacy construction must derive a single-value multi-value view");
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
