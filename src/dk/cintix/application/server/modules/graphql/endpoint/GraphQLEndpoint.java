package dk.cintix.application.server.modules.graphql.endpoint;

import com.google.gson.Gson;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.graphql.services.domain.GraphQLException;
import dk.cintix.application.server.modules.graphql.services.domain.ast.Document;
import dk.cintix.application.server.modules.graphql.services.domain.execution.Executor;
import dk.cintix.application.server.modules.graphql.services.domain.parser.Parser;
import dk.cintix.application.server.modules.graphql.services.domain.registry.GraphQLRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps the GraphQL engine as an HTTP POST endpoint.
 *
 * @author cix
 */
public class GraphQLEndpoint {

    private static final Logger logger = Logger.getLogger(GraphQLEndpoint.class.getName());

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
            String graphqlQuery = query == null ? "" : query;
            Parser parser = new Parser(graphqlQuery);
            Document doc = parser.parse();
            Map<String, Object> result = executor.execute(doc);
            String json = gson.toJson(result);
            return new Response().OK().ContentType("application/json").data(json);
        } catch (GraphQLException e) {
            return errorResponse(400, e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GraphQL execution failed", e);
            return errorResponse(500, "Internal server error");
        }
    }

    private Response errorResponse(int status, String message) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message != null ? message : "GraphQL error");
        List<Map<String, Object>> errors = new ArrayList<>();
        errors.add(error);
        errorBody.put("errors", errors);
        return new Response().status(status).ContentType("application/json").data(gson.toJson(errorBody));
    }
}
