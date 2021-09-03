/*
 */
package dk.cintix.tinyserver.rest.jsd.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cix
 */
public class ServiceAction {

    public enum ActionType {
        GET, POST, PUT, DELETE
    };

    private ActionType action;
    private String uri;
    private String accepts = "*/*";

    private final List<Cache> caching = new ArrayList<>();
    private final Map<String, String> arguments = new LinkedHashMap<>();

    public void addArgument(String name, String action) {
        arguments.put(name, action);
    }

    public void addCache(Cache cache) {
        caching.add(cache);
    }

    public ActionType getAction() {
        return action;
    }

    public void setAction(ActionType action) {
        this.action = action;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public String getAccepts() {
        return accepts;
    }

    public void setAccepts(String accepts) {
        this.accepts = accepts;
    }

    @Override
    public String toString() {
        return "ServiceAction{" + "action=" + action + ", uri=" + uri + ", accepts=" + accepts + ", caching=" + caching + ", arguments=" + arguments + '}';
    }

}