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
        TestSupport.assertTrue(response.getStatus() != 200, "Malformed query should not return 200");
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
        TestSupport.assertTrue(response.getStatus() != 200, "Unknown query should not return 200");
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
        Map<String, Map<String, RestEndpoint>> pathMapping = (Map<String, Map<String, RestEndpoint>>) pathMappingField.get(null);

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
}
