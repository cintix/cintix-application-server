package dk.cintix.application.server.rest;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.rest.annotations.Action;
import dk.cintix.application.server.rest.annotations.Cache;
import dk.cintix.application.server.rest.http.request.RestHttpRequest;
import dk.cintix.application.server.rest.response.Response;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class RestActionCacheKeyTest {

    public void runAll() throws Exception {
        cacheKeysIncludeResolvedMethodArguments();
    }

    public void cacheKeysIncludeResolvedMethodArguments() throws Exception {
        // Arrange
        CachedEchoEndpoint endpointObject = new CachedEchoEndpoint();
        Method method = CachedEchoEndpoint.class.getDeclaredMethod("echo", String.class);
        RestEndpoint endpoint = new RestEndpoint("/echo/{value}", method, endpointObject);

        RestHttpRequest request = new RestHttpRequest(new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>(), null, "GET", "/echo/a", "");

        // Act
        Response first = new RestAction(endpoint, Arrays.asList("A")).process(request);
        Response second = new RestAction(endpoint, Arrays.asList("B")).process(request);

        // Assert
        String firstBody = bodyFromResponse(first);
        String secondBody = bodyFromResponse(second);
        TestSupport.assertEquals("A", firstBody, "First response body mismatch");
        TestSupport.assertEquals("B", secondBody, "Cache key should include argument values");
    }

    private String bodyFromResponse(Response response) {
        String raw = new String(response.build());
        int split = raw.indexOf("\n\n");
        if (split == -1) {
            return "";
        }
        return raw.substring(split + 2);
    }

    public static class CachedEchoEndpoint {
        @Action(path = "/echo/{value}", consume = "*/*")
        @Cache(timeToLive = 5000, size = 100)
        public Response echo(String value) {
            return new Response().OK().ContentType("text/plain").data(value);
        }
    }
}
