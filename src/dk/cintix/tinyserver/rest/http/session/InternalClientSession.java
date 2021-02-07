/*
 */
package dk.cintix.tinyserver.rest.http.session;

import dk.cintix.tinyserver.rest.response.Response;

/**
 *
 * @author cix
 */
public class InternalClientSession {

    private String sessionId;
    private Response response;

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

    @Override
    public String toString() {
        return "InternalClientSession{" + "sessionId=" + sessionId + ", response=" + response + '}';
    }

}