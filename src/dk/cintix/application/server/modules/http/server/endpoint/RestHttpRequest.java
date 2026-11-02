package dk.cintix.application.server.modules.http.server.endpoint;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author cix
 */
public class RestHttpRequest {

    private final Map<String, String> headers;
    private final Map<String, String> queryStrings;
    private final Map<String, List<String>> queryStringValues;
    private final Map<String, String> postParams;
    private final Map<String, List<String>> postParamValues;
    private final Map<String, Object> customObjects;
    private final InputStream inputStream;
    private final String contextPath;
    private final String method;
    private final String rawPost;

    public RestHttpRequest(Map<String, String> headers, Map<String, String> queryStrings, Map<String, String> postParams, InputStream inputStream, String method, String contextPath, String rawPost) {
        this(headers, queryStrings, postParams, null, null, inputStream, method, contextPath, rawPost);
    }

    /**
     * @param queryStringValues every value per query key, or {@code null} to
     *        derive a single-value view from {@code queryStrings}
     * @param postParamValues every value per form key, or {@code null} to
     *        derive a single-value view from {@code postParams}
     */
    public RestHttpRequest(Map<String, String> headers, Map<String, String> queryStrings,
            Map<String, String> postParams, Map<String, List<String>> queryStringValues,
            Map<String, List<String>> postParamValues, InputStream inputStream,
            String method, String contextPath, String rawPost) {
        this.headers = headers;
        this.queryStrings = queryStrings;
        this.postParams = postParams;
        this.queryStringValues = queryStringValues != null ? queryStringValues : singleValueView(queryStrings);
        this.postParamValues = postParamValues != null ? postParamValues : singleValueView(postParams);
        this.inputStream = inputStream;
        this.contextPath = contextPath;
        this.method = method;
        this.rawPost = rawPost;
        this.customObjects = new HashMap<>();
    }

    private static Map<String, List<String>> singleValueView(Map<String, String> values) {
        Map<String, List<String>> view = new HashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                List<String> single = new ArrayList<>(1);
                single.add(entry.getValue());
                view.put(entry.getKey(), single);
            }
        }
        return view;
    }

    public String getHeader(String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getQueryKey(String key) {
        if (queryStrings.containsKey(key)) {
            return queryStrings.get(key);
        }
        return null;
    }

    /**
     * Returns every value of a repeated query parameter, in the order sent.
     * {@code ?tag=a&tag=b} yields {@code ["a", "b"]}; an absent key yields an
     * empty list. {@link #getQueryKey(String)} still returns just the last
     * value for callers that predate multi-value support.
     */
    public List<String> getQueryStringValues(String key) {
        List<String> values = queryStringValues.get(key);
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String getPost(String key) {
        if (postParams.containsKey(key)) {
            return postParams.get(key);
        }
        return null;
    }

    /**
     * Returns every value of a repeated form field, in the order sent. An
     * absent key yields an empty list.
     */
    public List<String> getPostValues(String key) {
        List<String> values = postParamValues.get(key);
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public void addCustom(String key, Object obj){
        customObjects.put(key, obj);
    }

    public void clearCustom(String key) {
        if (customObjects.containsKey(key)){
            customObjects.remove(key);
        }
    }

    public Object getCustom(String key) {
        if (customObjects.containsKey(key)){
            return customObjects.get(key);
        }
        return null;
    }

    public Map<String, Object> getCustomObjects() {
        return customObjects;
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

    public void addHeader(String key, String value) {
        headers.put(key.toUpperCase(), value);
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
