package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public class RestHttpRequestHeaderTest {

    public void runAll() {
        getHeader_isCaseInsensitive_lowercaseLookup();
        getHeader_isCaseInsensitive_mixedCaseLookup();
        getHeader_isCaseInsensitive_uppercaseLookup();
        getHeader_isCaseInsensitive_storedLowercase_foundWithUppercase();
        getHeader_isCaseInsensitive_addHeaderUppercasesKey();
        getHeader_returnsNullForMissingKey();
        getHeaders_returnsOriginalMapUnchanged();
    }

    public void getHeader_isCaseInsensitive_lowercaseLookup() {
        // Arrange - headers stored as uppercase (simulating HttpUtil.parseHeaderKeys behavior)
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("AUTHORIZATION", "Bearer token123");
        headers.put("CONTENT-TYPE", "application/json");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act
        String authHeader = request.getHeader("authorization");
        String contentTypeHeader = request.getHeader("content-type");

        // Assert
        TestSupport.assertEquals("Bearer token123", authHeader,
                "Lowercase lookup should find AUTHORIZATION header");
        TestSupport.assertEquals("application/json", contentTypeHeader,
                "Lowercase lookup should find CONTENT-TYPE header");
    }

    public void getHeader_isCaseInsensitive_mixedCaseLookup() {
        // Arrange
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-CUSTOM-HEADER", "custom-value");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act - typical mixed-case inputs from HTTP clients
        String mixedCase = request.getHeader("X-Custom-Header");
        String lowerCase = request.getHeader("x-custom-header");

        // Assert
        TestSupport.assertEquals("custom-value", mixedCase,
                "Mixed-case lookup should find X-CUSTOM-HEADER header");
        TestSupport.assertEquals("custom-value", lowerCase,
                "All-lowercase lookup should find X-CUSTOM-HEADER header");
    }

    public void getHeader_isCaseInsensitive_uppercaseLookup() {
        // Arrange
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("HOST", "example.com");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act - uppercase still works (backwards compatible)
        String hostHeader = request.getHeader("HOST");

        // Assert
        TestSupport.assertEquals("example.com", hostHeader,
                "Uppercase lookup should still work");
    }

    public void getHeader_isCaseInsensitive_storedLowercase_foundWithUppercase() {
        // Arrange - simulate headers stored in lowercase (e.g. raw map from non-standard source)
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("authorization", "Bearer lowercase-token");
        headers.put("content-type", "text/html");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act - lookup with different cases
        String upperLookup = request.getHeader("AUTHORIZATION");
        String mixedLookup = request.getHeader("Authorization");

        // Assert
        TestSupport.assertEquals("Bearer lowercase-token", upperLookup,
                "Uppercase lookup should find lowercase-stored authorization header");
        TestSupport.assertEquals("Bearer lowercase-token", mixedLookup,
                "Mixed-case lookup should find lowercase-stored authorization header");
    }

    public void getHeader_isCaseInsensitive_addHeaderUppercasesKey() {
        // Arrange
        Map<String, String> headers = new LinkedHashMap<>();
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act - addHeader should normalize key to uppercase
        request.addHeader("authorization", "Bearer token123");
        request.addHeader("X-Custom-Header", "custom-value");

        // Assert - getHeader works regardless
        TestSupport.assertEquals("Bearer token123", request.getHeader("Authorization"),
                "addHeader should store key uppercase so getHeader works with any case");
        TestSupport.assertEquals("custom-value", request.getHeader("x-custom-header"),
                "addHeader should store custom header uppercase so getHeader works with any case");
    }

    public void getHeader_returnsNullForMissingKey() {
        // Arrange
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("HOST", "example.com");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act
        String missingHeader = request.getHeader("Authorization");

        // Assert
        TestSupport.assertNull(missingHeader,
                "Missing header should return null");
    }

    public void getHeaders_returnsOriginalMapUnchanged() {
        // Arrange
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("AUTHORIZATION", "Bearer token123");
        RestHttpRequest request = new RestHttpRequest(headers, new LinkedHashMap<>(), new LinkedHashMap<>(), null, "GET", "/", "");

        // Act
        Map<String, String> returnedHeaders = request.getHeaders();

        // Assert - getHeaders() returns the raw map with original keys
        TestSupport.assertEquals("Bearer token123", returnedHeaders.get("AUTHORIZATION"),
                "getHeaders() should preserve original uppercase keys");
    }
}
