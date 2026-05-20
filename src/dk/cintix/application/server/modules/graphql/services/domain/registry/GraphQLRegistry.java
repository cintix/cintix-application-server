package dk.cintix.application.server.modules.graphql.services.domain.registry;

import dk.cintix.application.server.modules.graphql.GraphQLModule.Mutation;
import dk.cintix.application.server.modules.graphql.GraphQLModule.Query;
import java.lang.reflect.*;
import java.util.*;

public class GraphQLRegistry {

    private final Map<String, Method> queries = new HashMap<>();
    private final Map<String, Method> mutations = new HashMap<>();
    private final Map<Method, Object> services = new HashMap<>();

    public void register(Object service) {
        Class<?> clazz = service.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Query.class)) {
                queries.put(method.getAnnotation(Query.class).value(), method);
                services.put(method, service);
            }
            if (method.isAnnotationPresent(Mutation.class)) {
                mutations.put(method.getAnnotation(Mutation.class).value(), method);
                services.put(method, service);
            }
        }
    }

    public Method getQuery(String name) { return queries.get(name); }
    public Method getMutation(String name) { return mutations.get(name); }
    public Object getService(Method m) { return services.get(m); }
}
