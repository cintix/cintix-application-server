package dk.cintix.application.server.modules.http.server.endpoint;

import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author cix
 */
public class InternalClientSession {

    private String sessionId;
    private Response response;
    private ByteBuffer writeBuffer;
    private RequestAccumulator readAccumulator;
    private final Map<String, Object> keys = new LinkedHashMap<>();

    public InternalClientSession() {
    }

    public InternalClientSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public InternalClientSession(String sessionId, Response response) {
        this.sessionId = sessionId;
        this.response = response;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public void add(String key, Object obj) {
        keys.put(key, obj);
    }

    /**
     * The in-flight request buffer for this connection, created on first use.
     *
     * <p>It lives on the session so that a request split across several reads
     * keeps accumulating rather than being parsed truncated. {@code handleWrite}
     * replaces the session object before the connection is re-registered for
     * reading, so the buffer naturally belongs to exactly one request.</p>
     */
    RequestAccumulator getReadAccumulator() {
        if (readAccumulator == null) {
            readAccumulator = new RequestAccumulator();
        }
        return readAccumulator;
    }

    /**
     * Drops the buffered request bytes. Called once the request has been parsed
     * so the memory is released before the response is written.
     */
    void clearReadAccumulator() {
        readAccumulator = null;
    }

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    public void setWriteBuffer(ByteBuffer writeBuffer) {
        this.writeBuffer = writeBuffer;
    }

    public Object get(String key) {
        if (keys.containsKey(key)) {
            return keys.get(key);
        }
        return null;
    }

    @Override
    public String toString() {
        return "InternalClientSession{" + "sessionId=" + sessionId + ", response=" + response + '}';
    }

}
