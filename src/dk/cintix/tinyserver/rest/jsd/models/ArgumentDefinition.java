/*
 */
package dk.cintix.tinyserver.rest.jsd.models;

/**
 *
 * @author cix
 */
public class ArgumentDefinition {

    private String name;
    private String type;
    private ModelDefinition model;

    public ArgumentDefinition() {
    }

    public ArgumentDefinition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ModelDefinition getModel() {
        return model;
    }

    public void setModel(ModelDefinition model) {
        this.model = model;
    }

    @Override
    public String toString() {
        return "ArgumentDefinition{" + "name=" + name + ", type=" + type + ", model=" + model + '}';
    }

}
