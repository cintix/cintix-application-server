/*
 */
package dk.cintix.tinyserver.model;

/**
 *
 * @author cix
 */
public abstract class ModelGenerator {
        public abstract String fromModel(Object model);
        public abstract <T> T toModel(String content, Class<T> cls);
}
