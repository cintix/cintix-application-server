package dk.cintix.application.server.modules.http.server.endpoint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * RFC 6455 WebSocket frame encoder/decoder.
 *
 * @author cix
 */
public final class WebSocketFrame {

    private WebSocketFrame() {}

    public static final int OP_TEXT = 0x1;
    public static final int OP_BINARY = 0x2;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    public static final String MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static String computeAcceptKey(String clientKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((clientKey + MAGIC_GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    public static final class Frame {
        public boolean fin;
        public int opcode;
        public boolean masked;
        public byte[] payload;
    }

    /**
     * Extract complete frames from the buffer. Partial frame data remains in the buffer.
     */
    public static List<Frame> parseFrames(ByteBuffer buffer) {
        List<Frame> frames = new ArrayList<>();
        buffer.flip();

        while (buffer.remaining() >= 2) {
            int position = buffer.position();
            byte first = buffer.get();
            byte second = buffer.get();

            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0F;
            boolean masked = (second & 0x80) != 0;
            long payloadLength = second & 0x7F;

            if (payloadLength == 126) {
                if (buffer.remaining() < 2) {
                    buffer.position(position);
                    break;
                }
                payloadLength = buffer.getShort() & 0xFFFF;
            } else if (payloadLength == 127) {
                if (buffer.remaining() < 8) {
                    buffer.position(position);
                    break;
                }
                payloadLength = buffer.getLong();
            }

            byte[] maskKey = null;
            if (masked) {
                if (buffer.remaining() < 4) {
                    buffer.position(position);
                    break;
                }
                maskKey = new byte[4];
                buffer.get(maskKey);
            }

            if (buffer.remaining() < payloadLength) {
                buffer.position(position);
                break;
            }

            byte[] payload = new byte[(int) payloadLength];
            buffer.get(payload);

            if (masked && maskKey != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            Frame frame = new Frame();
            frame.fin = fin;
            frame.opcode = opcode;
            frame.masked = masked;
            frame.payload = payload;
            frames.add(frame);
        }

        buffer.compact();
        return frames;
    }

    public static byte[] encodeText(String payload) {
        return encodeFrame(true, OP_TEXT, payload.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encodeBinary(byte[] payload) {
        return encodeFrame(true, OP_BINARY, payload);
    }

    public static byte[] encodeClose(int statusCode, String reason) {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((statusCode >> 8) & 0xFF);
        payload[1] = (byte) (statusCode & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        return encodeFrame(true, OP_CLOSE, payload);
    }

    public static byte[] encodePong(byte[] pingPayload) {
        return encodeFrame(true, OP_PONG, pingPayload);
    }

    public static byte[] encodePing() {
        return encodeFrame(true, OP_PING, new byte[0]);
    }

    private static byte[] encodeFrame(boolean fin, int opcode, byte[] payload) {
        int capacity = 2 + payload.length;
        if (payload.length > 125) {
            capacity += (payload.length > 65535) ? 8 : 2;
        }

        ByteBuffer buffer = ByteBuffer.allocate(capacity);

        byte first = (byte) (opcode & 0x0F);
        if (fin) {
            first |= 0x80;
        }
        buffer.put(first);

        if (payload.length <= 125) {
            buffer.put((byte) payload.length);
        } else if (payload.length <= 65535) {
            buffer.put((byte) 126);
            buffer.putShort((short) payload.length);
        } else {
            buffer.put((byte) 127);
            buffer.putLong(payload.length);
        }

        buffer.put(payload);
        buffer.flip();

        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }
}
