package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.GET;
import dk.cintix.application.server.infrastructure.annotations.McpParam;
import dk.cintix.application.server.infrastructure.annotations.McpTool;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.mcp.McpDispatcher;
import dk.cintix.application.server.modules.mcp.McpRegistry;
import java.util.List;
import java.util.Map;

public class McpDispatcherTest {

    public void runAll() {
        registersToolsFromHandler();
        getToolListReturnsCorrectSchema();
        jsonSchemaTypeMapsCorrectly();
        invokeToolCallsMethodWithArgs();
        dispatcherReturnsInitializeCapabilities();
        dispatcherReturnsToolsList();
        unknownToolReturnsError();
        unknownMethodReturnsError();
        parseErrorReturnsError();
        autoDiscoveryFromEndpoints();
        missingToolNameReturnsError();
        invokeToolWithNoParams();
    }

    // --- Happy paths ---

    public void registersToolsFromHandler() {
        // Arrange
        McpRegistry registry = new McpRegistry();

        // Act
        registry.register(new TestToolHandler());

        // Assert
        McpRegistry.ToolEntry tool = registry.getTool("greet");
        TestSupport.assertTrue(tool != null, "should register greet tool");
        TestSupport.assertEquals("greet", tool.name, "tool name");
        TestSupport.assertEquals("Say hello", tool.description, "tool description");
    }

    public void getToolListReturnsCorrectSchema() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        registry.register(new TestToolHandler());

        // Act
        List<Map<String, Object>> tools = registry.getToolList();

        // Assert
        TestSupport.assertEquals(2, tools.size(), "should have 2 tools (greet + ping)");
        // Find greet tool in the list
        Map<String, Object> greetTool = null;
        for (Map<String, Object> t : tools) {
            if ("greet".equals(t.get("name"))) {
                greetTool = t;
                break;
            }
        }
        TestSupport.assertTrue(greetTool != null, "should have greet tool");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) greetTool.get("inputSchema");
        TestSupport.assertEquals("object", inputSchema.get("type"), "inputSchema type");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        TestSupport.assertTrue(properties.containsKey("name"), "should have name property");
    }

    public void jsonSchemaTypeMapsCorrectly() {
        // Assert
        TestSupport.assertEquals("string", McpRegistry.jsonSchemaType(String.class), "String -> string");
        TestSupport.assertEquals("integer", McpRegistry.jsonSchemaType(int.class), "int -> integer");
        TestSupport.assertEquals("integer", McpRegistry.jsonSchemaType(Integer.class), "Integer -> integer");
        TestSupport.assertEquals("integer", McpRegistry.jsonSchemaType(Long.class), "Long -> integer");
        TestSupport.assertEquals("number", McpRegistry.jsonSchemaType(double.class), "double -> number");
        TestSupport.assertEquals("number", McpRegistry.jsonSchemaType(Double.class), "Double -> number");
        TestSupport.assertEquals("boolean", McpRegistry.jsonSchemaType(boolean.class), "boolean -> boolean");
        TestSupport.assertEquals("boolean", McpRegistry.jsonSchemaType(Boolean.class), "Boolean -> boolean");
        TestSupport.assertEquals("object", McpRegistry.jsonSchemaType(Map.class), "Map -> object");
        TestSupport.assertEquals("array", McpRegistry.jsonSchemaType(List.class), "List -> array");
    }

    public void invokeToolCallsMethodWithArgs() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        TestToolHandler handler = new TestToolHandler();
        registry.register(handler);
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"World\"}}}"
        );

        // Assert
        TestSupport.assertTrue(response.containsKey("result"), "should have result");
        TestSupport.assertEquals("2.0", response.get("jsonrpc"), "should be jsonrpc 2.0");
    }

    public void dispatcherReturnsInitializeCapabilities() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"
        );

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        TestSupport.assertEquals("2024-11-05", result.get("protocolVersion"), "wrong protocol version");

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        TestSupport.assertTrue(capabilities.containsKey("tools"), "should have tools capability");
        TestSupport.assertTrue(capabilities.containsKey("resources"), "should have resources capability");
    }

    public void dispatcherReturnsToolsList() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        registry.register(new TestToolHandler());
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"
        );

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        TestSupport.assertTrue(tools.size() > 0, "should have at least one tool");
    }

    public void autoDiscoveryFromEndpoints() {
        // Arrange
        RestHttpServer server = new RestHttpServer() {};
        server.addEndpoint("/api", new EndpointWithTool());
        McpRegistry registry = new McpRegistry();
        registry.scanRegisteredEndpoints(server.getRegisteredEndpoints());

        // Act
        McpRegistry.ToolEntry tool = registry.getTool("get_status");

        // Assert
        TestSupport.assertTrue(tool != null, "should auto-discover tool from endpoint");
        TestSupport.assertEquals("get_status", tool.name, "tool name from auto-discovery");
    }

    public void invokeToolWithNoParams() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        registry.register(new TestToolHandler());
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"ping\",\"arguments\":{}}}"
        );

        // Assert
        TestSupport.assertTrue(response.containsKey("result"), "should have result for no-param tool");
        TestSupport.assertFalse(response.containsKey("error"), "should not have error");
    }

    // --- Unhappy paths ---

    public void unknownToolReturnsError() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"nonexistent\"}}"
        );

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        TestSupport.assertTrue(result.containsKey("isError"), "should have isError flag");
    }

    public void unknownMethodReturnsError() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"bogus\"}"
        );

        // Assert
        TestSupport.assertTrue(response.containsKey("error"), "should have error for unknown method");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        TestSupport.assertEquals(-32601, error.get("code"), "error code should be -32601");
    }

    public void parseErrorReturnsError() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle("not json");

        // Assert
        TestSupport.assertTrue(response.containsKey("error"), "should have error for bad JSON");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        TestSupport.assertEquals(-32700, error.get("code"), "error code should be -32700");
    }

    public void missingToolNameReturnsError() {
        // Arrange
        McpRegistry registry = new McpRegistry();
        McpDispatcher dispatcher = new McpDispatcher(registry);

        // Act
        Map<String, Object> response = dispatcher.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{}}"
        );

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        TestSupport.assertTrue(result.containsKey("isError"), "should have isError when tool name missing");
    }

    // --- Test classes ---

    public static class TestToolHandler {

        @McpTool(name = "greet", description = "Say hello")
        public String greet(
                @McpParam(name = "name", description = "Name to greet") String name) {
            return "Hello, " + name + "!";
        }

        @McpTool(name = "ping", description = "Check connectivity")
        public String ping() {
            return "pong";
        }
    }

    public static class EndpointWithTool {

        @GET
        @Action(path = "/api/status")
        @McpTool(name = "get_status", description = "Get system status")
        public Response status() {
            return new Response().OK().data("ok");
        }
    }
}
