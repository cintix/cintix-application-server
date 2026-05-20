package dk.cintix.application.server.modules.graphql.services;

import dk.cintix.application.server.infrastructure.modules.PluginContext;
import dk.cintix.application.server.modules.graphql.GraphQLModule;
import dk.cintix.application.server.modules.graphql.endpoint.GraphQLEndpoint;
import dk.cintix.application.server.modules.http.server.HttpModule;

/**
 * Facade service for the GraphQL plugin.
 *
 * @author cix
 */
public class GraphQLModuleService implements GraphQLModule {
    private HttpModule httpModule;

    @Override
    public String getName() {
        return "graphql";
    }

    @Override
    public void register(PluginContext context) {
        this.httpModule = context.getHttpModule();
        context.registerModule(GraphQLModule.class, this);
    }

    @Override
    public void addEndpoint(String path, Object service) {
        addEndpoint(path, new Object[]{service});
    }

    @Override
    public void addEndpoint(String path, Object... services) {
        if (httpModule == null) {
            throw new IllegalStateException("GraphQL module has not been registered");
        }
        httpModule.addEndpoint(path, new GraphQLEndpoint(services));
    }
}
