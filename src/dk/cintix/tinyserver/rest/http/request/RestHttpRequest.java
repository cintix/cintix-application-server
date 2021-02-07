/*
 */
package dk.cintix.tinyserver.rest.http.request;

import java.io.InputStream;
import java.util.Map;

/**
 *
 * @author cix
 */
public class RestHttpRequest {

    private final Map<String, String> headers;
    private final Map<String, String> queryStrings;
    private final Map<String, String> postParams;
    private final InputStream inputStream;
    private final String contextPath;
    private final String method;
    private final String rawPost;

    public RestHttpRequest(Map<String, String> headers, Map<String, String> queryStrings, Map<String, String> postParams, InputStream inputStream, String method, String contextPath, String rawPost) {
        this.headers = headers;
        this.queryStrings = queryStrings;
        this.postParams = postParams;
        this.inputStream = inputStream;
        this.contextPath = contextPath;
        this.method = method;
        this.rawPost = rawPost;
    }

    public String getHeader(String key) {
        if (headers.containsKey(key)) {
            return headers.get(key);
        }
        return null;
    }

    public String getQueryKey(String key) {
        if (queryStrings.containsKey(key)) {
            return queryStrings.get(key);
        }
        return null;
    }

    public String getPost(String key) {
        if (postParams.containsKey(key)) {
            return postParams.get(key);
        }
        return null;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryStrings() {
        return queryStrings;
    }

    public Map<String, String> getPostParams() {
        return postParams;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getMethod() {
        return method;
    }

    public String getContentType() {
        for (String key : headers.keySet()) {
            if (key.toLowerCase().equals("content-type")) {
                return headers.get(key);
            }
        }
        return "*/*";
    }

    public String getRawPost() {
        return rawPost;
    }

    @Override
    public String toString() {
        return "RestHttpRequest{" + "headers=" + headers + ", queryStrings=" + queryStrings + ", postParams=" + postParams + ", inputStream=" + inputStream + ", contextPath=" + contextPath + ", method=" + method + ", rawPost=" + rawPost + '}';
    }

}