/*
 */
package dk.cintix.tinyserver.rest.jsd.models;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cix
 */
public class API {

    private final List<Service> services = new ArrayList<>();

    public void addService(Service s) {
        services.add(s);
    }

    @Override
    public String toString() {
        return "API{" + "services=" + services + '}';
    }

}
