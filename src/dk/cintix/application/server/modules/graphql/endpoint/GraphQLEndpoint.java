package dk.cintix.application.server.modules.graphql.endpoint;

import com.google.gson.Gson;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.graphql.services.domain.ast.Document;
import dk.cintix.application.server.modules.graphql.services.domain.execution.Executor;
import dk.cintix.application.server.modules.graphql.services.domain.parser.Parser;
import dk.cintix.application.server.modules.graphql.services.domain.registry.GraphQLRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps the GraphQL engine as an HTTP POST endpoint.
 *
 * @author cix
 */
public class GraphQLEndpoint {

    private final GraphQLRegistry registry;
    private final Executor executor;
    private final Gson gson;

    public GraphQLEndpoint(Object... services) {
        this.registry = new GraphQLRegistry();
        for (Object service : services) {
            registry.register(service);
        }
        this.executor = new Executor(registry);
        this.gson = new Gson();
    }

    @POST
    @Action(path = "/")
    public Response handle(String query) {
        try {
            Parser parser = new Parser(query);
            Document doc = parser.parse();
            Map<String, Object> result = executor.execute(doc);
            String json = gson.toJson(result);
            return new Response().OK().ContentType("application/json").data(json);
        } catch (Exception e) {
            try {
                Map<String, Object> errorBody = new LinkedHashMap<>();
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("message", e.getMessage() != null ? e.getMessage() : "GraphQL execution error");
                java.util.List<Map<String, Object>> errors = new java.util.ArrayList<>();
                errors.add(error);
                errorBody.put("errors", errors);
                String json = gson.toJson(errorBody);
                return new Response().BadRequest().ContentType("application/json").data(json);
            } catch (Exception ex) {
                return new Response().InternalServerError().data("{\"errors\":[{\"message\":\"Internal error\"}]}");
            }
        }
    }
}
