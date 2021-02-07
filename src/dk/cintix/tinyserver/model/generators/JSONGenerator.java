/*
 */
package dk.cintix.tinyserver.model.generators;

import com.google.gson.Gson;
import dk.cintix.tinyserver.model.ModelGenerator;

/**
 *
 * @author cix
 */
public class JSONGenerator extends ModelGenerator {
    private final Gson gson = new Gson();

    @Override
    public String fromModel(Object model) {
        return gson.toJson(model);
    }

    @Override
    public <T> T toModel(String content, Class<T> cls) {
        return gson.fromJson(content, cls);
    }
}