package dk.cintix.application.server.modules.openapi;

import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.ApiDoc;
import dk.cintix.application.server.infrastructure.annotations.ApiParam;
import dk.cintix.application.server.infrastructure.annotations.ApiSchema;
import dk.cintix.application.server.infrastructure.annotations.ApiTag;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenApiService {

    private final String title;
    private final String version;
    private final String securityScheme;
    private final Class<?>[] schemaClasses;
    private final Map<String, Map<String, RestEndpoint>> endpoints;

    public OpenApiService(String title, String version, Map<String, Map<String, RestEndpoint>> endpoints) {
        this(title, version, "cookie", endpoints);
    }

    public OpenApiService(String title, String version, String securityScheme,
            Map<String, Map<String, RestEndpoint>> endpoints, Class<?>... schemaClasses) {
        this.title = title;
        this.version = version;
        this.securityScheme = securityScheme != null && !securityScheme.isEmpty() ? securityScheme : "cookie";
        this.schemaClasses = schemaClasses != null ? schemaClasses : new Class<?>[0];
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
        tags.add(tagFor(handlerClass, doc));
        op.put("tags", tags);

        // Parameters from {placeholders} with @ApiParam enrichment
        List<Map<String, Object>> params = buildParams(method, action);
        if (!params.isEmpty()) {
            op.put("parameters", params);
        }

        // Request body for POST/PUT — auto-generate schema from body params
        if ("post".equals(httpMethod) || "put".equals(httpMethod)) {
            List<Parameter> bodyParams = getBodyParameters(method, action);
            if (!bodyParams.isEmpty()) {
                Map<String, Object> body = buildRequestBody(method, bodyParams, doc);
                op.put("requestBody", body);
            }
        }

        // Responses
        op.put("responses", buildResponses(doc));

        // Security
        op.put("security", buildSecurity());

        return op;
    }

    /**
     * Builds path parameter definitions, enriched with @ApiParam annotations.
     */
    private List<Map<String, Object>> buildParams(Method method, Action action) {
        List<Map<String, Object>> params = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(action.path());
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", paramName);
            p.put("in", "path");
            p.put("required", true);

            // Look for @ApiParam on method parameters matching this path param
            ApiParam apiParam = findApiParam(method, paramName);
            String type = "string";
            String desc = "";
            if (apiParam != null) {
                if (!apiParam.type().isEmpty()) type = apiParam.type();
                if (!apiParam.description().isEmpty()) desc = apiParam.description();
            }
            if (!desc.isEmpty()) p.put("description", desc);
            p.put("schema", typeMap(type));
            params.add(p);
        }
        return params;
    }

    /**
     * Returns method parameters that are NOT path variables — these form the request body.
     */
    private List<Parameter> getBodyParameters(Method method, Action action) {
        List<Parameter> bodyParams = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(action.path());
        int pathParamCount = 0;
        while (matcher.find()) {
            pathParamCount++;
        }

        Parameter[] allParams = method.getParameters();
        for (int i = pathParamCount; i < allParams.length; i++) {
            bodyParams.add(allParams[i]);
        }
        return bodyParams;
    }

    /**
     * Builds a request body definition with auto-generated JSON Schema from method
     * parameters, plus an example. @ApiDoc.example overrides the auto-generated example.
     */
    private Map<String, Object> buildRequestBody(Method method, List<Parameter> bodyParams, ApiDoc doc) {
        Map<String, Object> body = new LinkedHashMap<>();
        String bodyDesc = (doc != null && !doc.requestBody().isEmpty()) ? doc.requestBody() : "Request body";
        body.put("description", bodyDesc);
        body.put("required", bodyParams.size() == 1 ? false : true);

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> requiredList = new ArrayList<>();
        Map<String, Object> example = new LinkedHashMap<>();

        for (Parameter param : bodyParams) {
            ApiParam apiParam = param.getAnnotation(ApiParam.class);
            String propName = param.getName();
            String propType = jsonSchemaType(param.getType());
            String propDesc = "";

            if (apiParam != null) {
                if (!apiParam.name().isEmpty()) propName = apiParam.name();
                if (!apiParam.type().isEmpty()) propType = apiParam.type();
                if (!apiParam.description().isEmpty()) propDesc = apiParam.description();
                if (apiParam.required()) requiredList.add(propName);
            } else {
                requiredList.add(propName);
            }

            Map<String, Object> propSchema = new LinkedHashMap<>();
            propSchema.put("type", propType);
            if (!propDesc.isEmpty()) propSchema.put("description", propDesc);
            properties.put(propName, propSchema);

            example.put(propName, exampleValueForType(propType, propName));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!requiredList.isEmpty()) {
            schema.put("required", requiredList);
        }

        String contentType = (doc != null && !doc.contentType().isEmpty()) ? doc.contentType() : "application/json";
        Map<String, Object> mediaType = new LinkedHashMap<>();
        mediaType.put("schema", schema);

        // Example: use @ApiDoc.example if set, otherwise auto-generated
        if (doc != null && !doc.example().isEmpty()) {
            try {
                mediaType.put("example", new com.google.gson.Gson().fromJson(doc.example(), Object.class));
            } catch (Exception e) {
                mediaType.put("example", doc.example());
            }
        } else if (!example.isEmpty()) {
            mediaType.put("example", example);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put(contentType, mediaType);
        body.put("content", content);

        return body;
    }

    private Map<String, Object> buildResponses(ApiDoc doc) {
        Map<String, Object> responses = new LinkedHashMap<>();

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", (doc != null && !doc.response200().isEmpty()) ? doc.response200() : "Successful");

        if (doc != null && !doc.responseExample().isEmpty()) {
            Map<String, Object> jsonContent = new LinkedHashMap<>();
            Map<String, Object> mediaType = new LinkedHashMap<>();
            try {
                mediaType.put("example", new com.google.gson.Gson().fromJson(doc.responseExample(), Object.class));
            } catch (Exception e) {
                mediaType.put("example", doc.responseExample());
            }
            jsonContent.put("application/json", mediaType);
            ok.put("content", jsonContent);
        }
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

        return responses;
    }

    private List<Map<String, Object>> buildSecurity() {
        List<Map<String, Object>> security = new ArrayList<>();
        Map<String, Object> secEntry = new LinkedHashMap<>();
        String schemeName = "cookie".equals(securityScheme) ? "cookieAuth" : "bearerAuth";
        secEntry.put(schemeName, new ArrayList<>());
        security.add(secEntry);
        return security;
    }

    private String summaryFor(Method method, ApiDoc doc) {
        if (doc != null && !doc.summary().isEmpty()) {
            return doc.summary();
        }
        String name = method.getName();
        return name.replaceAll("([A-Z])", " $1").trim().toLowerCase();
    }

    /**
     * Tag priority: @ApiDoc.tag > @ApiTag.name > auto from class name
     */
    private String tagFor(Class<?> handlerClass, ApiDoc doc) {
        if (doc != null && !doc.tag().isEmpty()) {
            return doc.tag();
        }
        ApiTag classTag = handlerClass.getAnnotation(ApiTag.class);
        if (classTag != null && !classTag.name().isEmpty()) {
            return classTag.name();
        }
        return handlerClass.getSimpleName().replace("Endpoint", "");
    }

    private Map<String, Object> components() {
        Map<String, Object> comp = new LinkedHashMap<>();

        // Security schemes
        Map<String, Object> sec = new LinkedHashMap<>();
        if ("bearer".equals(securityScheme)) {
            Map<String, Object> bearer = new LinkedHashMap<>();
            bearer.put("type", "http");
            bearer.put("scheme", "bearer");
            bearer.put("bearerFormat", "JWT");
            sec.put("bearerAuth", bearer);
        } else {
            Map<String, Object> cookie = new LinkedHashMap<>();
            cookie.put("type", "apiKey");
            cookie.put("in", "cookie");
            cookie.put("name", "session");
            sec.put("cookieAuth", cookie);
        }
        comp.put("securitySchemes", sec);

        // Schemas from @ApiSchema classes
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (Class<?> cls : schemaClasses) {
            ApiSchema schema = cls.getAnnotation(ApiSchema.class);
            if (schema != null) {
                String schemaName = !schema.name().isEmpty() ? schema.name() : cls.getSimpleName();
                Map<String, Object> schemaDef = new LinkedHashMap<>();
                schemaDef.put("type", "object");
                if (!schema.description().isEmpty()) {
                    schemaDef.put("description", schema.description());
                }

                Map<String, Object> schemaProps = new LinkedHashMap<>();
                for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                    Map<String, Object> fieldSchema = new LinkedHashMap<>();
                    fieldSchema.put("type", jsonSchemaType(field.getType()));
                    schemaProps.put(field.getName(), fieldSchema);
                }
                if (!schemaProps.isEmpty()) {
                    schemaDef.put("properties", schemaProps);
                }
                schemas.put(schemaName, schemaDef);
            }
        }
        if (!schemas.isEmpty()) {
            comp.put("schemas", schemas);
        } else {
            Map<String, Object> bodySchema = new LinkedHashMap<>();
            bodySchema.put("type", "object");
            schemas.put("body", bodySchema);
            comp.put("schemas", schemas);
        }

        return comp;
    }

    private static ApiParam findApiParam(Method method, String paramName) {
        for (Parameter p : method.getParameters()) {
            ApiParam ap = p.getAnnotation(ApiParam.class);
            if (ap != null) {
                String name = !ap.name().isEmpty() ? ap.name() : p.getName();
                if (name.equals(paramName)) {
                    return ap;
                }
            }
            if (p.getName().equals(paramName)) {
                return p.getAnnotation(ApiParam.class);
            }
        }
        return null;
    }

    private static String jsonSchemaType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class) return "integer";
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (Map.class.isAssignableFrom(type)) return "object";
        if (List.class.isAssignableFrom(type) || type.isArray()) return "array";
        return "string";
    }

    private static String exampleValueForType(String jsonType, String name) {
        if ("integer".equals(jsonType)) return "0";
        if ("number".equals(jsonType)) return "0.0";
        if ("boolean".equals(jsonType)) return "false";
        return name;
    }

    private static Map<String, Object> typeMap(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }
}
