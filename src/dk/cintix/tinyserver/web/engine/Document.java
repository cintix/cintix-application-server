package dk.cintix.tinyserver.web.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author migo
 */
public class Document implements Cloneable {

    private final String file;
    private final long modified;
    private Map<String, Instance> variables = new LinkedHashMap<>();
    private String data;
    private Map<String, String> requestQueryStrings;
    private Map<String, String> postFields;
    private Map<String, String> requestFields;
    private String requestedUrl;
    private boolean post = false;
    
    public Document(String file, long modified) {
        this.file = file;
        this.modified = modified;
    }

    public void setVariables(Map<String, Instance> variables) {
        this.variables = variables;
    }

    public Map<String, Instance> getVariables() {
        return variables;
    }

    public String getFile() {
        return file;
    }

    public long getModified() {
        return modified;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void replace(Instance instance, String replacement) {
        if (variables.containsKey(instance.getName())) {
            String replacementKey = "${" + instance.getName() + "}";
            int where = data.indexOf(replacementKey);
            int to = where + replacementKey.length();
            data = data.substring(0, where) + replacement + data.substring(to);
        }
    }

    public Map<String, String> getRequestQueryStrings() {
        return requestQueryStrings;
    }

    public void setRequestQueryStrings(Map<String, String> requestQueryStrings) {
        this.requestQueryStrings = requestQueryStrings;
    }

    public Map<String, String> getPostFields() {
        return postFields;
    }

    public void setPostFields(Map<String, String> postFields) {
        this.postFields = postFields;
    }

    public Map<String, String> getRequestFields() {
        return requestFields;
    }

    public void setRequestFields(Map<String, String> requestFields) {
        this.requestFields = requestFields;
    }

    public boolean isPost() {
        return post;
    }

    public void setPost(boolean post) {
        this.post = post;
    }

    public String getRequestedUrl() {
        return requestedUrl;
    }

    public void setRequestedUrl(String requestedUrl) {
        this.requestedUrl = requestedUrl;
    }
    
    @Override
    public Document clone() throws CloneNotSupportedException {
        return (Document) super.clone();
    }

    @Override
    public String toString() {
        return "Document{" + "file=" + file + ", modified=" + modified + ", variables=" + variables + ", data=" + data + '}';
    }

}
