/*
 */
package dk.cintix.application.server.rest.jsd.models;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cix
 */
public class Service {

    private String name;
    private String uri;

    private final List<ServiceAction> methods = new ArrayList<>();

    public void addMethod(ServiceAction action) {
        methods.add(action);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<ServiceAction> getMethods() {
        return methods;
    }

    @Override
    public String toString() {
        return "Service{" + "name=" + name + ", uri=" + uri + ", methods=" + methods + '}';
    }

}
