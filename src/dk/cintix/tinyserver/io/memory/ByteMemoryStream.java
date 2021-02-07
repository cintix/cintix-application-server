/*
 */
package dk.cintix.tinyserver.io.memory;

/**
 *
 * @author cix
 */
public class ByteMemoryStream {

    private byte[] bytes = new byte[0];

    public void writeBytes(byte[] content) {
        int length = bytes.length + content.length;
        byte[] tmp = new byte[length];
        int newIndex = 0;
        for (int index = 0; index < bytes.length; index++) {
            tmp[newIndex] = bytes[index];
            newIndex++;
        }
        for (int index = 0; index < content.length; index++) {
            tmp[newIndex] = content[index];
            newIndex++;
        }
        bytes = tmp;
        tmp = null;
    }

    public byte[] toByteArray() {
        return bytes;
    }

}
