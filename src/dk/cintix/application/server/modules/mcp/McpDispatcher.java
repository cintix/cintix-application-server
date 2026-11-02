package dk.cintix.application.server.modules.mcp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.cintix.application.server.infrastructure.ReflectionUtil;
import dk.cintix.application.server.infrastructure.annotations.McpParam;
import java.lang.reflect.InvocationTargetException;
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

    /**
     * Handles one JSON-RPC message.
     *
     * @return the response object, or {@code null} when the message is a
     *         JSON-RPC notification (no {@code id}). The transport must answer
     *         a notification with {@code 202 Accepted} and no body.
     */
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

        // A valid request without an id is a notification: JSON-RPC 2.0 forbids
        // a response body, so the transport answers 202 and sends nothing.
        if (!req.containsKey("id")) {
            return null;
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
            return invoke(method, handler, new Object[0]);
        }

        Object[] arguments = new Object[methodParams.length];
        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            // The schema advertises @McpParam(name = ...), so binding must use
            // the same name rather than the Java parameter name.
            String paramName = parameterName(param);
            arguments[i] = convertArgument(param, args.get(paramName));
        }

        return invoke(method, handler, arguments);
    }

    private static Object invoke(java.lang.reflect.Method method, Object handler, Object[] arguments) throws Exception {
        try {
            return method.invoke(handler, arguments);
        } catch (InvocationTargetException e) {
            // Report the tool's own failure, not the reflection wrapper.
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static String parameterName(Parameter param) {
        McpParam annotation = param.getAnnotation(McpParam.class);
        if (annotation != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }
        return param.getName();
    }

    /**
     * Converts a JSON value to the Java parameter type.
     *
     * <p>Gson decodes every JSON number to {@code Double}, so the old
     * {@code String.valueOf(rawValue)} path produced {@code "3.0"} and
     * {@code Integer.parseInt} failed — then the raw {@code Double} was passed
     * to {@code method.invoke}, which threw
     * {@code IllegalArgumentException: argument type mismatch}. Numbers are now
     * narrowed directly from {@link Number}.</p>
     */
    private static Object convertArgument(Parameter param, Object rawValue) throws Exception {
        if (rawValue == null) {
            return null;
        }

        Class<?> type = param.getType();
        if (type == String.class) {
            return rawValue instanceof String ? rawValue : String.valueOf(rawValue);
        }
        if (type == int.class || type == Integer.class) {
            return (int) toLong(rawValue);
        }
        if (type == long.class || type == Long.class) {
            return toLong(rawValue);
        }
        if (type == short.class || type == Short.class) {
            return (short) toLong(rawValue);
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) toLong(rawValue);
        }
        if (type == double.class || type == Double.class) {
            return toDouble(rawValue);
        }
        if (type == float.class || type == Float.class) {
            return (float) toDouble(rawValue);
        }
        if (type == boolean.class || type == Boolean.class) {
            if (rawValue instanceof Boolean) {
                return rawValue;
            }
            if (rawValue instanceof Number) {
                return ((Number) rawValue).doubleValue() != 0;
            }
            return Boolean.parseBoolean(String.valueOf(rawValue).trim());
        }
        if (type == char.class || type == Character.class) {
            String text = String.valueOf(rawValue);
            return text.isEmpty() ? '\0' : text.charAt(0);
        }
        if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object constant = Enum.valueOf((Class<? extends Enum>) type, String.valueOf(rawValue));
            return constant;
        }

        // Dates and anything else ReflectionUtil understands.
        Object converted = ReflectionUtil.valueFromType(param, String.valueOf(rawValue));
        return converted != null ? converted : rawValue;
    }

    private static long toLong(Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        String text = String.valueOf(rawValue).trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // Tolerate "3.0" from clients that send an integral value as a float.
            return (long) Double.parseDouble(text);
        }
    }

    private static double toDouble(Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).doubleValue();
        }
        return Double.parseDouble(String.valueOf(rawValue).trim());
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

    /**
     * Builds a tool-level failure. MCP represents a failing tool as a normal
     * result with {@code isError: true}, but the message must still reach the
     * client: the old version returned empty content for every failure, so a
     * missing name, an unknown tool and an invocation error were
     * indistinguishable.
     */
    private Map<String, Object> errorInResult(int code, String message) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("text", message);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(text);

        Map<String, Object> result = buildMap("content", content, "isError", true);
        result.put("_meta", buildMap("errorCode", code));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result", result);
        return r;
    }
}
