package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;

public class RestHttpServerPathTest {

    public void runAll() {
        documentRootStripsTrailingSlashSafely();
    }

    public void documentRootStripsTrailingSlashSafely() {
        // Arrange
        RestHttpServer server = new RestHttpServer() {};

        // Act
        server.setDocumentRoot("web///");
        String root = server.getDocumentRoot();

        // Assert
        TestSupport.assertEquals("web", root, "Document root should not end with '/'");
    }
}
