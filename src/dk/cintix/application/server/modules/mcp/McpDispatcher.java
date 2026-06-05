package dk.cintix.application.server.modules.mcp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.cintix.application.server.infrastructure.ReflectionUtil;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpDispatcher {

    private final McpRegistry registry;
    private final Gson gson = new Gson();
    private static final String MCP_VERSION = "2024-11-05";

    public McpDispatcher(McpRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(String body) {
        Map<String, Object> req;
        try {
            req = gson.fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            return error(-32700, "Parse error", null);
        }

        if (req == null) {
            return error(-32600, "Invalid Request", null);
        }

        String method = (String) req.get("method");
        Object id = req.get("id");

        if (method == null) {
            return error(-32600, "Invalid Request: missing method", id);
        }

        try {
            Map<String, Object> result;
            if ("initialize".equals(method)) {
                result = handleInitialize();
            } else if ("tools/list".equals(method)) {
                result = handleToolsList();
            } else if ("tools/call".equals(method)) {
                result = handleToolsCall(req);
            } else if ("resources/list".equals(method)) {
                result = handleResourcesList();
            } else {
                result = error(-32601, "Method not found: " + method, id);
                result.put("jsonrpc", "2.0");
                if (id != null) result.put("id", id);
                return result;
            }

            result.put("jsonrpc", "2.0");
            if (id != null) {
                result.put("id", id);
            }
            return result;
        } catch (Exception e) {
            return error(-32603, e.getMessage() != null ? e.getMessage() : "Internal error", id);
        }
    }

    private Map<String, Object> handleInitialize() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("tools", buildMap("listChanged", false));
        caps.put("resources", buildMap("listChanged", false));

        result.put("result", buildMap(
            "protocolVersion", MCP_VERSION,
            "capabilities", caps,
            "serverInfo", buildMap("name", "cintix-application-server", "version", "1.0.0")
        ));
        return result;
    }

    private Map<String, Object> handleToolsList() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", buildMap("tools", registry.getToolList()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> req) {
        Map<String, Object> params = (Map<String, Object>) req.getOrDefault("params", new LinkedHashMap<>());
        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", new LinkedHashMap<>());

        if (toolName == null) {
            return errorInResult(-32602, "Missing tool name");
        }

        McpRegistry.ToolEntry tool = registry.getTool(toolName);
        if (tool == null) {
            return errorInResult(-32602, "Unknown tool: " + toolName);
        }

        try {
            Object output = invokeTool(tool, args);
            String outputJson = gson.toJson(output != null ? output : buildMap("success", true));

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("type", "text");
            content.put("text", outputJson);
            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(content);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("result", buildMap("content", contentList));
            return result;
        } catch (Exception e) {
            return errorInResult(-32000, e.getMessage() != null ? e.getMessage() : "Tool error");
        }
    }

    private Map<String, Object> handleResourcesList() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", buildMap("resources", new ArrayList<>()));
        return result;
    }

    private Object invokeTool(McpRegistry.ToolEntry tool, Map<String, Object> args) throws Exception {
        java.lang.reflect.Method method = tool.method;
        Object handler = tool.handler;
        Parameter[] methodParams = method.getParameters();

        if (methodParams.length == 0) {
            return method.invoke(handler);
        }

        Object[] arguments = new Object[methodParams.length];
        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            String paramName = param.getName();
            Object rawValue = args.get(paramName);

            if (rawValue == null) {
                arguments[i] = null;
                continue;
            }

            String stringValue = String.valueOf(rawValue);
            try {
                Object converted = ReflectionUtil.valueFromType(param, stringValue);
                if (converted != null) {
                    arguments[i] = converted;
                } else {
                    // Fallback for complex types: pass the raw value as-is (e.g. Map, List)
                    arguments[i] = rawValue;
                }
            } catch (Exception e) {
                arguments[i] = rawValue;
            }
        }

        return method.invoke(handler, arguments);
    }

    private Map<String, Object> buildMap(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Map<String, Object> error(int code, String message, Object id) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("jsonrpc", "2.0");
        if (id != null) {
            err.put("id", id);
        }
        err.put("error", buildMap("code", code, "message", message));
        return err;
    }

    private Map<String, Object> errorInResult(int code, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result", buildMap("content", new ArrayList<>(), "isError", true));
        return r;
    }
}
