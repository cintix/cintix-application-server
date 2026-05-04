package dk.cintix.application.server.web;

import dk.cintix.application.server.TestSupport;

public class MimeTypesTest {

    public void runAll() {
        contentType_resolvesKnownTypeCaseInsensitive();
        contentType_returnsDefaultForUnknownOrNull();
    }

    public void contentType_resolvesKnownTypeCaseInsensitive() {
        // Arrange
        String ext = "JSON";

        // Act
        String contentType = MimeTypes.ContentType(ext);

        // Assert
        TestSupport.assertEquals("application/json", contentType, "Known extension lookup failed");
    }

    public void contentType_returnsDefaultForUnknownOrNull() {
        // Arrange
        String unknown = "unknownext";

        // Act
        String unknownType = MimeTypes.ContentType(unknown);
        String nullType = MimeTypes.ContentType(null);

        // Assert
        TestSupport.assertEquals("application/octet-stream", unknownType, "Unknown extension fallback failed");
        TestSupport.assertEquals("application/octet-stream", nullType, "Null extension fallback failed");
    }
}
