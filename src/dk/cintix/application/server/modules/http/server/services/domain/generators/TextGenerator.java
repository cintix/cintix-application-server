package dk.cintix.application.server.modules.http.server.services.domain.generators;

import dk.cintix.application.server.modules.http.server.services.domain.ModelGenerator;

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
