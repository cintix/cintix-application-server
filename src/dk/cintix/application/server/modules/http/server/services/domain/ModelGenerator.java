package dk.cintix.application.server.modules.http.server.services.domain;

/**
 *
 * @author cix
 */
public abstract class ModelGenerator {
    public abstract String fromModel(Object model);
    public abstract <T> T toModel(String content, Class<T> cls);
}
