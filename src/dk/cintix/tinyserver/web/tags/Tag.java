/*
 */
package dk.cintix.tinyserver.web.tags;

import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.web.engine.Instance;
import java.util.Map;

/**
 *
 *
 */
public abstract class Tag {

    private RestHttpRequest httpRequest;

    public void setHttpRequest(RestHttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    public Map<String, Object> getCustomObjects() {
        return httpRequest.getCustomObjects();
    }

    public Map<String, String> getQuery() {
        return httpRequest.getQueryStrings();
    }

    public Map<String, String> getPost() {
        return httpRequest.getPostParams();
    }

    public String getHeader(String name) {
        return httpRequest.getHeader(name);
    }

    public abstract String toHTML(Map<String, Instance> variables);

}
