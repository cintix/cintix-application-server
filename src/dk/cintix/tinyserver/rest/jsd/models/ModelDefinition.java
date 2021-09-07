/*
 */
package dk.cintix.tinyserver.rest.jsd.models;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cix
 */
public class ModelDefinition {

    private final List<ArgumentDefinition> fields = new ArrayList<>();

    public ModelDefinition() {
    }

    public void addDefinition(ArgumentDefinition ad) {
        fields.add(ad);
    }

    @Override
    public String toString() {
        return "ModelDefinition{" + "fields=" + fields + '}';
    }

}
