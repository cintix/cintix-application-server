/*
 */
package dk.cintix.tinyserver.model.generators;

import dk.cintix.tinyserver.model.ModelGenerator;

/**
 *
 * @author cix
 */
public class TextGenerator extends ModelGenerator {

    @Override
    public String fromModel(Object model) {
        return model.toString();
    }

    @Override
    public <T> T toModel(String content, Class<T> cls) {
        return (T) content;
    }
}