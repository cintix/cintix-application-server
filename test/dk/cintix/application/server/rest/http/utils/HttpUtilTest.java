package dk.cintix.application.server.rest.http.utils;

import dk.cintix.application.server.TestSupport;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpUtilTest {

    public void runAll() {
        parseQueryStrings_extractsPathAndParameters();
        parseQueryStrings_handlesEmptyAssignedValue();
        parseHeaderKeys_keepsColonInsideHeaderValue();
        parsePostFields_handlesEmptyAssignedValue();
        buildContextPath_joinsSegmentsWithoutTrailingSlash();
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

    public void buildContextPath_joinsSegmentsWithoutTrailingSlash() {
        // Arrange
        String[] segments = new String[]{"api", "v1", "users", "123"};

        // Act
        String path = HttpUtil.buildContextPath(segments);

        // Assert
        TestSupport.assertEquals("api/v1/users", path, "Context path build failed");
    }
}
