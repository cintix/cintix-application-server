package dk.cintix.application.server.modules.http.server.endpoint;

import dk.cintix.application.server.modules.http.server.services.domain.models.WebSocketSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket session implementation wrapping a SocketChannel.
 *
 * @author cix
 */
public class WebSocketSessionImpl implements WebSocketSession.SocketChannelWriter {

    private final SocketChannel channel;
    private final SelectionKey key;
    private final WebSocketSession session;
    private final List<byte[]> outgoingFrames = new ArrayList<>();
    private boolean closeFrameSent;

    public WebSocketSessionImpl(SocketChannel channel, SelectionKey key, WebSocketSession session) {
        this.channel = channel;
        this.key = key;
        this.session = session;
        this.session.setWriter(this);
    }

    public WebSocketSession getSession() {
        return session;
    }

    public SelectionKey getKey() {
        return key;
    }

    public boolean hasOutgoingFrames() {
        synchronized (outgoingFrames) {
            return !outgoingFrames.isEmpty();
        }
    }

    public boolean isCloseFrameSent() {
        return closeFrameSent;
    }

    @Override
    public void enqueueText(String text) {
        enqueue(WebSocketFrame.encodeText(text));
    }

    @Override
    public void enqueueBinary(byte[] data) {
        enqueue(WebSocketFrame.encodeBinary(data));
    }

    @Override
    public void enqueuePing() {
        enqueue(WebSocketFrame.encodePing());
    }

    @Override
    public void enqueueClose(int statusCode, String reason) {
        enqueue(WebSocketFrame.encodeClose(statusCode, reason));
        closeFrameSent = true;
    }

    private void enqueue(byte[] frame) {
        synchronized (outgoingFrames) {
            outgoingFrames.add(frame);
        }
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        key.selector().wakeup();
    }

    public List<byte[]> drainOutgoingFrames() {
        synchronized (outgoingFrames) {
            List<byte[]> frames = new ArrayList<>(outgoingFrames);
            outgoingFrames.clear();
            return frames;
        }
    }

    public int writePending() throws IOException {
        List<byte[]> frames = drainOutgoingFrames();
        int written = 0;
        for (byte[] frame : frames) {
            ByteBuffer buffer = ByteBuffer.wrap(frame);
            while (buffer.hasRemaining()) {
                written += channel.write(buffer);
            }
        }
        key.interestOps(SelectionKey.OP_READ);
        return written;
    }
}
