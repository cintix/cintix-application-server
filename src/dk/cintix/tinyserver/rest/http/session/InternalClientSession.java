/*
 */
package dk.cintix.tinyserver.rest.http.session;

import dk.cintix.tinyserver.rest.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author cix
 */
public class InternalClientSession {

    private String sessionId;
    private Response response;
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
