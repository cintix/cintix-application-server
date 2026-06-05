package dk.cintix.application.server.modules.openapi;

import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.ApiDoc;
import dk.cintix.application.server.infrastructure.annotations.ApiTag;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenApiService {

    private final String title;
    private final String version;
    private final Map<String, Map<String, RestEndpoint>> endpoints;

    public OpenApiService(String title, String version, Map<String, Map<String, RestEndpoint>> endpoints) {
        this.title = title;
        this.version = version;
        this.endpoints = endpoints;
    }

    public Map<String, Object> generate() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", info());
        spec.put("servers", servers());
        spec.put("paths", paths());
        spec.put("components", components());
        return spec;
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", title);
        info.put("version", version);
        return info;
    }

    private List<Map<String, Object>> servers() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("url", "/");
        s.put("description", "Same origin");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(s);
        return list;
    }

    private Map<String, Object> paths() {
        Map<String, Object> paths = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, RestEndpoint>> methodEntry : endpoints.entrySet()) {
            String httpMethod = methodEntry.getKey();
            Map<String, RestEndpoint> endpointMap = methodEntry.getValue();

            for (Map.Entry<String, RestEndpoint> entry : endpointMap.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("^")) {
                    continue;
                }
                RestEndpoint endpoint = entry.getValue();
                Method method = endpoint.getMethod();
                Action action = method.getAnnotation(Action.class);
                if (action == null) {
                    continue;
                }

                String path = action.path();
                if (!paths.containsKey(path)) {
                    paths.put(path, new LinkedHashMap<>());
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> pathOps = (Map<String, Object>) paths.get(path);
                pathOps.put(httpMethod, operation(method, action, httpMethod, endpoint.getObject().getClass()));
            }
        }
        return paths;
    }

    private Map<String, Object> operation(Method method, Action action, String httpMethod, Class<?> handlerClass) {
        Map<String, Object> op = new LinkedHashMap<>();

        ApiDoc doc = method.getAnnotation(ApiDoc.class);
        op.put("summary", summaryFor(method, doc));
        op.put("operationId", method.getName());
        if (doc != null && !doc.description().isEmpty()) {
            op.put("description", doc.description());
        }
        if (doc != null && doc.deprecated()) {
            op.put("deprecated", true);
        }

        List<String> tags = new ArrayList<>();
        tags.add(tagFor(action.path(), handlerClass, doc));
        op.put("tags", tags);

        // Parameters from {placeholders}
        List<Map<String, Object>> params = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(action.path());
        while (matcher.find()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", matcher.group(1));
            p.put("in", "path");
            p.put("required", true);
            p.put("schema", typeMap("string"));
            params.add(p);
        }
        if (!params.isEmpty()) {
            op.put("parameters", params);
        }

        // Request body for POST/PUT
        if ("post".equals(httpMethod) || "put".equals(httpMethod)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("required", false);
            String bodyDesc = (doc != null && !doc.requestBody().isEmpty()) ? doc.requestBody() : "Request body";
            body.put("description", bodyDesc);
            Map<String, Object> jsonContent = new LinkedHashMap<>();
            jsonContent.put("application/json", schemaRef("body"));
            body.put("content", jsonContent);
            op.put("requestBody", body);
        }

        // Responses
        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", (doc != null && !doc.response200().isEmpty()) ? doc.response200() : "Successful");
        responses.put("200", ok);

        if (doc != null && !doc.response400().isEmpty()) {
            Map<String, Object> badReq = new LinkedHashMap<>();
            badReq.put("description", doc.response400());
            responses.put("400", badReq);
        }
        if (doc != null && !doc.response401().isEmpty()) {
            Map<String, Object> unauth = new LinkedHashMap<>();
            unauth.put("description", doc.response401());
            responses.put("401", unauth);
        }
        if (doc == null || doc.response401().isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("description", "Unauthorized");
            responses.put("401", err);
        }
        op.put("responses", responses);

        // Security
        List<Map<String, Object>> security = new ArrayList<>();
        Map<String, Object> secEntry = new LinkedHashMap<>();
        secEntry.put("cookieAuth", new ArrayList<>());
        security.add(secEntry);
        op.put("security", security);

        return op;
    }

    private String summaryFor(Method method, ApiDoc doc) {
        if (doc != null && !doc.summary().isEmpty()) {
            return doc.summary();
        }
        String name = method.getName();
        return name.replaceAll("([A-Z])", " $1").trim().toLowerCase();
    }

    private String tagFor(String path, Class<?> handlerClass, ApiDoc doc) {
        if (doc != null && !doc.tag().isEmpty()) {
            return doc.tag();
        }
        ApiTag classTag = handlerClass.getAnnotation(ApiTag.class);
        if (classTag != null && !classTag.name().isEmpty()) {
            return classTag.name();
        }
        // Auto-tag from path prefix
        String segment = path.replaceFirst("^/api/", "");
        if (segment.contains("/")) {
            segment = segment.substring(0, segment.indexOf("/"));
        }
        if (!segment.isEmpty()) {
            return segment.substring(0, 1).toUpperCase() + segment.substring(1);
        }
        return "General";
    }

    private Map<String, Object> components() {
        Map<String, Object> comp = new LinkedHashMap<>();

        Map<String, Object> sec = new LinkedHashMap<>();
        Map<String, Object> cookie = new LinkedHashMap<>();
        cookie.put("type", "apiKey");
        cookie.put("in", "cookie");
        cookie.put("name", "session");
        sec.put("cookieAuth", cookie);
        comp.put("securitySchemes", sec);

        Map<String, Object> schemas = new LinkedHashMap<>();
        Map<String, Object> bodySchema = new LinkedHashMap<>();
        bodySchema.put("type", "object");
        schemas.put("body", bodySchema);
        comp.put("schemas", schemas);

        return comp;
    }

    private static Map<String, Object> typeMap(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }

    private static Map<String, Object> schemaRef(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("$ref", "#/components/schemas/" + name);
        m.put("schema", ref);
        return m;
    }
}
