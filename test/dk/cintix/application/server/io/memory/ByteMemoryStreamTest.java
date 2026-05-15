package dk.cintix.application.server.io.memory;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.ByteMemoryStream;

public class ByteMemoryStreamTest {

    public void runAll() {
        writeBytes_appendsInOrder();
        writeBytes_ignoresNullAndEmptyInput();
    }

    public void writeBytes_appendsInOrder() {
        // Arrange
        ByteMemoryStream stream = new ByteMemoryStream();

        // Act
        stream.writeBytes(new byte[]{1, 2});
        stream.writeBytes(new byte[]{3, 4});

        // Assert
        TestSupport.assertArrayEquals(new byte[]{1, 2, 3, 4}, stream.toByteArray(), "Byte stream append failed");
    }

    public void writeBytes_ignoresNullAndEmptyInput() {
        // Arrange
        ByteMemoryStream stream = new ByteMemoryStream();

        // Act
        stream.writeBytes(null);
        stream.writeBytes(new byte[]{});

        // Assert
        TestSupport.assertEquals(0, stream.toByteArray().length, "Null/empty bytes should not alter stream");
    }
}
