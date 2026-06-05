package dk.cintix.application.server.modules.mcp;

import com.google.gson.Gson;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.Inject;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.util.Map;

public class McpEndpoint {

    @Inject
    private RestHttpRequest request;

    private final McpDispatcher dispatcher;
    private final Gson gson = new Gson();

    public McpEndpoint(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @POST
    @Action(path = "/mcp")
    public Response handle(String body) {
        try {
            Map<String, Object> result = dispatcher.handle(body);
            return new Response().OK().ContentType("application/json").data(gson.toJson(result));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Response().InternalServerError().ContentType("application/json")
                    .data("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"" + msg + "\"}}");
        }
    }
}
