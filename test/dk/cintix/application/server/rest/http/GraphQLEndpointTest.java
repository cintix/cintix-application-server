package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.modules.ModuleRegistry;
import dk.cintix.application.server.infrastructure.modules.PluginContext;
import dk.cintix.application.server.modules.graphql.GraphQLModule;
import dk.cintix.application.server.modules.graphql.endpoint.GraphQLEndpoint;
import dk.cintix.application.server.modules.graphql.services.GraphQLModuleService;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphQLEndpointTest {

    public void runAll() throws Exception {
        pluginRegistersGraphQLEndpoint();
        queryWithoutArguments_returnsJsonResult();
        queryWithArguments_passesArgumentsToMethod();
        mutation_executesAndReturnsResult();
        malformedQuery_returnsError();
        unknownQuery_returnsError();
        subSelection_projectsFields();
        queryWithUnknownArgument_returnsError();
        queryWithMissingArgument_returnsError();
        queryWithUnknownProjectionField_returnsError();
        numericArguments_convertToExpectedTypes();
        enumArgument_convertsToEnum();
        argumentTypeMismatch_returnsError();
        deeplyNestedQuery_returnsError();
        queryWithTooManySelections_returnsError();
        internalServiceError_returnsInternalErrorWithoutDetails();
    }

    public void pluginRegistersGraphQLEndpoint() throws Exception {
        // Arrange
        RestHttpServer server = new RestHttpServer() {};
        PluginContext context = ModuleRegistry.initialize(server, new GraphQLModuleService());
        GraphQLModule graphql = context.getModule(GraphQLModule.class);
        graphql.addEndpoint("/graphql", new HelloService());
        RestHttpRequest request = new RestHttpRequest(new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>(), null, "POST", "/graphql", "{ hello }");

        // Act
        Response response = handle(server, request);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertTrue(json.contains("hello"), "Plugin endpoint should contain hello key");
        TestSupport.assertTrue(json.contains("World"), "Plugin endpoint should contain result value");
    }

    public void queryWithoutArguments_returnsJsonResult() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new HelloService());
        String query = "query { hello }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertTrue(json.contains("hello"), "Response should contain hello key");
        TestSupport.assertTrue(json.contains("World"), "Response should contain result value");
    }

    public void queryWithArguments_passesArgumentsToMethod() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new GreetService());
        String query = "{ greet(name: \"Claude\") }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertTrue(json.contains("greet"), "Response should contain greet key");
        TestSupport.assertTrue(json.contains("Hello, Claude"), "Response should contain constructed greeting");
    }

    public void mutation_executesAndReturnsResult() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new CreateService());
        String query = "mutation { createItem(name: \"test\") { id, name } }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertTrue(json.contains("createItem"), "Response should contain createItem key");
        TestSupport.assertTrue(json.contains("id"), "Sub-selection should include id");
        TestSupport.assertTrue(json.contains("name"), "Sub-selection should include name");
        TestSupport.assertTrue(json.contains("test"), "Sub-selection should include input value");
    }

    public void malformedQuery_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new HelloService());
        String query = "not a valid query {{{";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Malformed query should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Error response should contain errors key");
    }

    public void unknownQuery_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new HelloService());
        String query = "query { nonexistent }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Unknown query should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Error response should contain errors key");
    }

    public void subSelection_projectsFields() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new UserService());
        String query = "{ user(id: \"1\") { name, email } }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertTrue(json.contains("user"), "Response should contain user key");
        TestSupport.assertTrue(json.contains("name"), "Sub-selection should include name");
        TestSupport.assertTrue(json.contains("email"), "Sub-selection should include email");
        TestSupport.assertTrue(json.contains("John"), "Sub-selection should include name value");
        TestSupport.assertTrue(json.contains("john@example.com"), "Sub-selection should include email value");
        TestSupport.assertFalse(json.contains("password"), "Non-requested fields should not leak");
    }

    public void queryWithUnknownArgument_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new GreetService());
        String query = "{ greet(who: \"Claude\") }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Unknown argument should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Unknown argument should produce an error");
        TestSupport.assertTrue(json.contains("who"), "Unknown argument name should be reported");
    }

    public void queryWithMissingArgument_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new GreetService());
        String query = "{ greet }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Missing argument should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Missing argument should produce an error");
        TestSupport.assertTrue(json.contains("name"), "Missing argument name should be reported");
    }

    public void queryWithUnknownProjectionField_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new UserService());
        String query = "{ user(id: \"1\") { nope } }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Unknown projection field should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Unknown projection field should produce an error");
        TestSupport.assertTrue(json.contains("nope"), "Unknown projection field name should be reported");
    }

    public void numericArguments_convertToExpectedTypes() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new NumericService());
        String query = "{ numbers(count: 3, big: 123456789012345, small: 7, tiny: 2, ratio: 1, amount: 2, active: true, label: \"ok\") }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(200, response.getStatus(), "Numeric arguments should be accepted");
        TestSupport.assertTrue(json.contains("123456789012345"), "Long argument should be converted");
        TestSupport.assertTrue(json.contains("\"label\":\"ok\""), "String argument should be converted");
        TestSupport.assertTrue(json.contains("\"ratio\":1.0"), "Float argument should be converted");
        TestSupport.assertTrue(json.contains("\"amount\":2.0"), "Double argument should be converted");
        TestSupport.assertTrue(json.contains("\"active\":true"), "Boolean argument should be converted");
    }

    public void enumArgument_convertsToEnum() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new EnumService());
        String query = "{ status(value: ACTIVE) }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(200, response.getStatus(), "Enum argument should be accepted");
        TestSupport.assertTrue(json.contains("ACTIVE"), "Enum argument should be converted to enum");
    }

    public void argumentTypeMismatch_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new GreetService());
        String query = "{ greet(name: 42) }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Type mismatch should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Type mismatch should produce an error");
        TestSupport.assertTrue(json.contains("string"), "Type mismatch should report the expected type");
    }

    public void deeplyNestedQuery_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new HelloService());
        String query = nestedQuery(11);

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Deeply nested query should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Deeply nested query should produce an error");
        TestSupport.assertTrue(json.contains("maximum depth"), "Deeply nested query should report the depth limit");
    }

    public void queryWithTooManySelections_returnsError() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new HelloService());
        String query = manySelectionsQuery(101);

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(400, response.getStatus(), "Too many selections should return 400");
        TestSupport.assertTrue(json.contains("errors"), "Too many selections should produce an error");
        TestSupport.assertTrue(json.contains("selections"), "Too many selections should report the selection limit");
    }

    public void internalServiceError_returnsInternalErrorWithoutDetails() throws Exception {
        // Arrange
        GraphQLEndpoint endpoint = new GraphQLEndpoint(new BrokenService());
        String query = "{ boom }";

        // Act
        Response response = endpoint.handle(query);
        String json = bodyFromResponse(response);

        // Assert
        TestSupport.assertEquals(500, response.getStatus(), "Internal service error should return 500");
        TestSupport.assertTrue(json.contains("errors"), "Internal service error should contain errors key");
        TestSupport.assertFalse(json.contains("top-secret-detail"), "Internal service error must not leak exception details");
    }

    private String nestedQuery(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("{ a ");
        }
        for (int i = 0; i < depth; i++) {
            sb.append(" }");
        }
        return sb.toString();
    }

    private String manySelectionsQuery(int count) {
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < count; i++) {
            sb.append("f").append(i).append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    private String bodyFromResponse(Response response) {
        String raw = new String(response.build());
        int split = raw.indexOf("\r\n\r\n");
        if (split == -1) {
            return "";
        }
        return raw.substring(split + 4);
    }

    @SuppressWarnings("unchecked")
    private Response handle(RestHttpServer server, RestHttpRequest request) throws Exception {
        Field pathMappingField = RestHttpServer.class.getDeclaredField("pathMapping");
        pathMappingField.setAccessible(true);
        Map<String, Map<String, RestEndpoint>> pathMapping = (Map<String, Map<String, RestEndpoint>>) pathMappingField.get(server);

        Method handleRequestMapping = RestHttpServer.class.getDeclaredMethod("handleRequestMapping", Map.class, RestHttpRequest.class);
        handleRequestMapping.setAccessible(true);
        return (Response) handleRequestMapping.invoke(server, pathMapping, request);
    }

    // -- Test services and models --

    public static class HelloService {
        @GraphQLModule.Query("hello")
        public String hello() {
            return "World";
        }
    }

    public static class GreetService {
        @GraphQLModule.Query("greet")
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    public static class CreateService {
        @GraphQLModule.Mutation("createItem")
        public Item createItem(String name) {
            Item item = new Item();
            item.id = "new-1";
            item.name = name;
            return item;
        }
    }

    public static class Item {
        public String id;
        public String name;
    }

    public static class UserService {
        @GraphQLModule.Query("user")
        public User user(String id) {
            User u = new User();
            u.name = "John";
            u.email = "john@example.com";
            u.password = "secret";
            return u;
        }
    }

    public static class User {
        public String name;
        public String email;
        public String password;
    }

    public static class NumericService {
        @GraphQLModule.Query("numbers")
        public Numeric numbers(int count, long big, short small, byte tiny, float ratio, double amount, boolean active, String label) {
            Numeric n = new Numeric();
            n.count = count;
            n.big = big;
            n.small = small;
            n.tiny = tiny;
            n.ratio = ratio;
            n.amount = amount;
            n.active = active;
            n.label = label;
            return n;
        }
    }

    public static class Numeric {
        public int count;
        public long big;
        public short small;
        public byte tiny;
        public float ratio;
        public double amount;
        public boolean active;
        public String label;
    }

    public enum StatusKind {
        ACTIVE, INACTIVE
    }

    public static class EnumService {
        @GraphQLModule.Query("status")
        public String status(StatusKind value) {
            return value.name();
        }
    }

    public static class BrokenService {
        @GraphQLModule.Query("boom")
        public String boom() {
            throw new IllegalStateException("top-secret-detail");
        }
    }
}
