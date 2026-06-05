package dk.cintix.application.server.modules.openapi;

import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.GET;
import dk.cintix.application.server.infrastructure.annotations.Inject;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenApiEndpoint {

    @Inject
    private RestHttpRequest request;

    private final OpenApiService openApiService;

    private static final String SWAGGER_HTML =
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>API Documentation</title>"
        + "<link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\">"
        + "<style>html{box-sizing:border-box;overflow:-moz-scrollbars-vertical;overflow-y:scroll}*,:after,:before{box-sizing:inherit}body{margin:0;background:#fafafa}"
        + ".swagger-ui .topbar{background-color:#2e4976}.swagger-ui .topbar .download-url-wrapper .select-label{display:flex;align-items:center;width:100%;max-width:600px}"
        + ".swagger-ui .info .title{font-size:28px}.swagger-ui .btn.authorize{background-color:#4c6ef5;border-color:#4c6ef5}"
        + ".swagger-ui .btn.authorize svg{fill:#fff}</style></head><body>"
        + "<div id=\"swagger-ui\"></div>"
        + "<script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>"
        + "<script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-standalone-preset.js\"></script>"
        + "<script>SwaggerUIBundle({url:'/api/openapi.json',dom_id:'#swagger-ui',deepLinking:true,presets:[SwaggerUIBundle.presets.apis,SwaggerUIStandalonePreset],layout:\"StandaloneLayout\"})</script>"
        + "</body></html>";

    public OpenApiEndpoint(OpenApiService service) {
        this.openApiService = service;
    }

    @GET
    @Action(path = "/openapi.json")
    public Response openApiSpec() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return new Response().OK().ContentType("application/json").data(gson.toJson(openApiService.generate()));
        } catch (Exception e) {
            return new Response().InternalServerError().data("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GET
    @Action(path = "/docs")
    public Response docs() {
        return new Response().OK().ContentType("text/html").data(SWAGGER_HTML);
    }
}
