package dk.cintix.application.server.modules.http.server.services.domain.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public session object for WebSocket connections.
 *
 * @author cix
 */
public class WebSocketSession {

    private final String id;
    private final String remoteAddress;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private boolean open = true;
    private SocketChannelWriter writer;

    public WebSocketSession(String id, String remoteAddress) {
        this.id = id;
        this.remoteAddress = remoteAddress;
    }

    public void setWriter(SocketChannelWriter writer) {
        this.writer = writer;
    }

    public String getId() {
        return id;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void send(String text) {
        if (!open || writer == null) {
            return;
        }
        writer.enqueueText(text);
    }

    public void send(byte[] data) {
        if (!open || writer == null) {
            return;
        }
        writer.enqueueBinary(data);
    }

    public void ping() {
        if (!open || writer == null) {
            return;
        }
        writer.enqueuePing();
    }

    public void close(int statusCode, String reason) {
        if (!open || writer == null) {
            return;
        }
        writer.enqueueClose(statusCode, reason);
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Internal interface for the session to write frames back to the channel.
     */
    public interface SocketChannelWriter {
        void enqueueText(String text);
        void enqueueBinary(byte[] data);
        void enqueuePing();
        void enqueueClose(int statusCode, String reason);
    }

    @Override
    public String toString() {
        return "WebSocketSession{" + "id=" + id + ", remoteAddress=" + remoteAddress + ", open=" + open + '}';
    }

}
