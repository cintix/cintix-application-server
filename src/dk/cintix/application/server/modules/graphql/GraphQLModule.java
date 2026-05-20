package dk.cintix.application.server.modules.graphql;

import dk.cintix.application.server.infrastructure.modules.Plugin;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Public contract for the GraphQL plugin module.
 *
 * @author cix
 */
public interface GraphQLModule extends Plugin {
    void addEndpoint(String path, Object service);
    void addEndpoint(String path, Object... services);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Query {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Mutation {
        String value();
    }
}
