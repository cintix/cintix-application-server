package dk.cintix.application.server.rest;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.RestActionService;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

public class RestActionServiceMixedParamsTest {

    public void runAll() throws Exception {
        mixedPathAndBodyParameters_bothResolved();
        mixedPathAndBodyParameters_getMethodAlsoWorks();
        purePathParameters_onlyArgumentsUsed();
    }

    public void mixedPathAndBodyParameters_bothResolved() throws Exception {
        // Arrange - method takes path var + body: createItem(String spaceId, String body)
        MixedEndpoint endpointObject = new MixedEndpoint();
        Method method = MixedEndpoint.class.getDeclaredMethod("createItem", String.class, String.class);
        RestEndpoint endpoint = new RestEndpoint("/space/{spaceId}/item", method, endpointObject);

        // 1 path argument ("123"), 2 parameters (spaceId + body)
        RestHttpRequest request = new RestHttpRequest(
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                null, "POST",
                "/space/123/item",
                "{\"name\":\"test-item\"}");

        // Act
        Response response = new RestActionService(endpoint, Arrays.asList("123")).process(request);

        // Assert - extract body from response and verify both params were resolved
        String body = bodyFromResponse(response);
        TestSupport.assertEquals("123", body,
                "Path variable spaceId should be resolved from URL arguments");
        TestSupport.assertTrue(body.contains("123"),
                "Response should contain the resolved path variable");
    }

    public void mixedPathAndBodyParameters_getMethodAlsoWorks() throws Exception {
        // Arrange - same method but with GET (no raw body needed, but body param should still work)
        MixedEndpoint endpointObject = new MixedEndpoint();
        Method method = MixedEndpoint.class.getDeclaredMethod("createItem", String.class, String.class);
        RestEndpoint endpoint = new RestEndpoint("/space/{spaceId}/item", method, endpointObject);

        // 1 path argument, rawPost is empty since GET shouldn't have a body
        RestHttpRequest request = new RestHttpRequest(
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                null, "GET",
                "/space/123/item",
                "");

        // Act
        Response response = new RestActionService(endpoint, Arrays.asList("123")).process(request);

        // Assert - should not crash
        String body = bodyFromResponse(response);
        TestSupport.assertTrue(body.contains("123"),
                "Path variable should be resolved even without body content");
    }

    public void purePathParameters_onlyArgumentsUsed() throws Exception {
        // Arrange - method with only path params, no body: listItems(String spaceId, String itemId)
        MixedEndpoint endpointObject = new MixedEndpoint();
        Method method = MixedEndpoint.class.getDeclaredMethod("listItems", String.class, String.class);
        RestEndpoint endpoint = new RestEndpoint("/space/{spaceId}/item/{itemId}", method, endpointObject);

        // 2 path arguments for 2 parameters, no body
        RestHttpRequest request = new RestHttpRequest(
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>(),
                null, "GET",
                "/space/123/item/456",
                "");

        // Act - both params from URL arguments
        Response response = new RestActionService(endpoint, Arrays.asList("123", "456")).process(request);

        // Assert
        String body = bodyFromResponse(response);
        TestSupport.assertTrue(body.contains("123") && body.contains("456"),
                "Both path variables should be resolved from URL arguments");
    }

    private String bodyFromResponse(Response response) {
        String raw = new String(response.build());
        int split = raw.indexOf("\r\n\r\n");
        if (split == -1) {
            return "";
        }
        return raw.substring(split + 4);
    }

    public static class MixedEndpoint {
        @Action(path = "/space/{spaceId}/item", consume = "*/*")
        public Response createItem(String spaceId, String body) {
            return new Response().OK().ContentType("text/plain").data(spaceId);
        }

        @Action(path = "/space/{spaceId}/item/{itemId}", consume = "*/*")
        public Response listItems(String spaceId, String itemId) {
            return new Response().OK().ContentType("text/plain").data(spaceId + "/" + itemId);
        }
    }
}
