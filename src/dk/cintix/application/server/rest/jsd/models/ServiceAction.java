/*
 */
package dk.cintix.application.server.rest.jsd.models;

import java.util.ArrayList;
import java.util.List;

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
    private final List<ArgumentDefinition> arguments = new ArrayList<>();

    public void addArgument(ArgumentDefinition action) {
        arguments.add(action);
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

    public List<ArgumentDefinition> getArguments() {
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