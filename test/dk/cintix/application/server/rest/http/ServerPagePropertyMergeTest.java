package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import java.util.Map;
import java.util.TreeMap;

public class ServerPagePropertyMergeTest {

    public void runAll() {
        mergesQueryAndPostWithQueryOverridingPostOnKeyCollision();
    }

    public void mergesQueryAndPostWithQueryOverridingPostOnKeyCollision() {
        // Arrange
        Map<String, String> post = new TreeMap<>();
        post.put("name", "postName");
        post.put("postOnly", "x");

        Map<String, String> query = new TreeMap<>();
        query.put("name", "queryName");
        query.put("queryOnly", "y");

        // Act
        Map<String, String> merged = new TreeMap<>();
        merged.putAll(post);
        merged.putAll(query);

        // Assert
        TestSupport.assertEquals("queryName", merged.get("name"), "Query key should override post key");
        TestSupport.assertEquals("x", merged.get("postOnly"), "postOnly key missing");
        TestSupport.assertEquals("y", merged.get("queryOnly"), "queryOnly key missing");
    }
}
