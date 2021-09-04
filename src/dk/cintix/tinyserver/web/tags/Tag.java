/*
 */
package dk.cintix.tinyserver.web.tags;

import dk.cintix.tinyserver.web.engine.Instance;
import java.util.Map;

/**
 *
 *
 */
public abstract class Tag {

    protected Map<String, String> query;
    protected Map<String, String> request;
    protected Map<String, String> post;

    public Map<String, String> getQuery() {
        return query;
    }

    public void setQuery(Map<String, String> query) {
        this.query = query;
    }

    public Map<String, String> getRequest() {
        return request;
    }

    public void setRequest(Map<String, String> request) {
        this.request = request;
    }

    public Map<String, String> getPost() {
        return post;
    }

    public void setPost(Map<String, String> post) {
        this.post = post;
    }

    public abstract String toHTML(Map<String, Instance> variables);

}
