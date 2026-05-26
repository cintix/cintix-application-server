package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.services.domain.models.WebSocketSession;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebSocketQueryStringTest {

    public void runAll() {
        sessionAttributes_storeAndRetrieveQueryStrings();
        sessionAttributes_multipleQueryStrings();
        sessionAttributes_noQueryStrings_emptyMap();
    }

    /**
     * Simulates what handleUpgrade does: query strings are copied to session
     * attributes before @OnOpen is invoked. The handler can then access them
     * via session.getAttribute("qs.token") etc.
     */
    public void sessionAttributes_storeAndRetrieveQueryStrings() {
        // Arrange - simulate the query strings that would come from request.getQueryStrings()
        Map<String, String> queryStrings = new LinkedHashMap<>();
        queryStrings.put("token", "abc123");
        queryStrings.put("mode", "presence");

        WebSocketSession session = new WebSocketSession("session-1", "127.0.0.1:54321");

        // Act - copy query strings to session attributes (as handleUpgrade now does)
        for (Map.Entry<String, String> qs : queryStrings.entrySet()) {
            session.addAttribute("qs." + qs.getKey(), qs.getValue());
        }

        // Assert - handler can retrieve them
        TestSupport.assertEquals("abc123", (String) session.getAttribute("qs.token"),
                "Query string 'token' should be available as session attribute");
        TestSupport.assertEquals("presence", (String) session.getAttribute("qs.mode"),
                "Query string 'mode' should be available as session attribute");
    }

    public void sessionAttributes_multipleQueryStrings() {
        // Arrange
        Map<String, String> queryStrings = new LinkedHashMap<>();
        queryStrings.put("token", "xyz789");
        queryStrings.put("room", "general");
        queryStrings.put("user", "alice");

        WebSocketSession session = new WebSocketSession("session-2", "192.168.1.1:9000");

        // Act
        for (Map.Entry<String, String> qs : queryStrings.entrySet()) {
            session.addAttribute("qs." + qs.getKey(), qs.getValue());
        }

        // Assert
        TestSupport.assertEquals("xyz789", (String) session.getAttribute("qs.token"),
                "Token should be available");
        TestSupport.assertEquals("general", (String) session.getAttribute("qs.room"),
                "Room should be available");
        TestSupport.assertEquals("alice", (String) session.getAttribute("qs.user"),
                "User should be available");
        TestSupport.assertNull(session.getAttribute("qs.missing"),
                "Unset query string should return null");
    }

    public void sessionAttributes_noQueryStrings_emptyMap() {
        // Arrange
        Map<String, String> queryStrings = new LinkedHashMap<>();
        WebSocketSession session = new WebSocketSession("session-3", "10.0.0.1:3000");

        // Act - no query strings to copy, nothing happens
        for (Map.Entry<String, String> qs : queryStrings.entrySet()) {
            session.addAttribute("qs." + qs.getKey(), qs.getValue());
        }

        // Assert - session still works, no qs.* attributes set
        TestSupport.assertNull(session.getAttribute("qs.token"),
                "No query strings means no qs.* attributes");
        TestSupport.assertTrue(session.isOpen(), "Session should still be open");
    }
}
