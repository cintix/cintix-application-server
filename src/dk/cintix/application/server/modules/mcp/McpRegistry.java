package dk.cintix.application.server.modules.mcp;

import dk.cintix.application.server.infrastructure.annotations.McpParam;
import dk.cintix.application.server.infrastructure.annotations.McpTool;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpRegistry {

    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();

    public static class ToolEntry {
        public final String name;
        public final String description;
        public final Method method;
        public final Object handler;
        public final List<ParamSchema> parameters;

        public ToolEntry(String name, String description, Method method, Object handler, List<ParamSchema> parameters) {
            this.name = name;
            this.description = description;
            this.method = method;
            this.handler = handler;
            this.parameters = parameters;
        }
    }

    public static class ParamSchema {
        public final String name;
        public final String type;
        public final String description;
        public final boolean required;

        public ParamSchema(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }
    }

    public void register(Object handler) {
        Class<?> cls = handler.getClass();
        for (Method method : cls.getDeclaredMethods()) {
            McpTool tool = method.getAnnotation(McpTool.class);
            if (tool == null) {
                continue;
            }
            String name = tool.name().isEmpty() ? method.getName() : tool.name();
            String description = tool.description();

            List<ParamSchema> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                McpParam mcpParam = param.getAnnotation(McpParam.class);
                String paramName = (mcpParam != null && !mcpParam.name().isEmpty())
                        ? mcpParam.name() : param.getName();
                String paramDesc = (mcpParam != null) ? mcpParam.description() : "";
                boolean required = (mcpParam == null) || mcpParam.required();
                params.add(new ParamSchema(paramName, jsonSchemaType(param.getType()), paramDesc, required));
            }

            tools.put(name, new ToolEntry(name, description, method, handler, params));
        }
    }

    public void scanRegisteredEndpoints(Map<String, Map<String, RestEndpoint>> pathMapping) {
        for (Map.Entry<String, Map<String, RestEndpoint>> methodEntry : pathMapping.entrySet()) {
            for (Map.Entry<String, RestEndpoint> entry : methodEntry.getValue().entrySet()) {
                if (entry.getKey().startsWith("^")) {
                    continue;
                }
                RestEndpoint endpoint = entry.getValue();
                register(endpoint.getObject());
            }
        }
    }

    public List<Map<String, Object>> getToolList() {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (ToolEntry entry : tools.values()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (ParamSchema p : entry.parameters) {
                Map<String, Object> propSchema = new LinkedHashMap<>();
                propSchema.put("type", p.type);
                if (!p.description.isEmpty()) {
                    propSchema.put("description", p.description);
                }
                properties.put(p.name, propSchema);
            }

            List<String> required = new ArrayList<>();
            for (ParamSchema p : entry.parameters) {
                if (p.required) {
                    required.add(p.name);
                }
            }

            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");
            inputSchema.put("properties", properties);
            if (!required.isEmpty()) {
                inputSchema.put("required", required);
            }

            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", entry.name);
            toolMap.put("description", entry.description);
            toolMap.put("inputSchema", inputSchema);
            toolList.add(toolMap);
        }
        return toolList;
    }

    public ToolEntry getTool(String name) {
        return tools.get(name);
    }

    public static String jsonSchemaType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }
        if (List.class.isAssignableFrom(type) || type.isArray()) {
            return "array";
        }
        return "string";
    }
}
