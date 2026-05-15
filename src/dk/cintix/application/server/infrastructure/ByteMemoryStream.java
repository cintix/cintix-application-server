package dk.cintix.application.server.infrastructure;

/**
 *
 * @author cix
 */
public class ByteMemoryStream {

    private byte[] bytes = new byte[0];

    public void writeBytes(byte[] content) {
        if (content == null || content.length == 0) {
            return;
        }
        int length = bytes.length + content.length;
        byte[] tmp = new byte[length];
        System.arraycopy(bytes, 0, tmp, 0, bytes.length);
        System.arraycopy(content, 0, tmp, bytes.length, content.length);
        bytes = tmp;
    }

    public byte[] toByteArray() {
        return bytes;
    }

}
