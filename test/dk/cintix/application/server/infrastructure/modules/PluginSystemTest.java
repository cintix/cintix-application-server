package dk.cintix.application.server.infrastructure.modules;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.modules.graphql.GraphQLModule;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import dk.cintix.application.server.modules.ratelimit.RateLimitModule;
import dk.cintix.application.server.modules.ratelimit.services.RateLimitModuleService;
import dk.cintix.application.server.modules.scheduler.SchedulerModule;
import dk.cintix.application.server.modules.scheduler.services.SchedulerModuleService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PluginSystemTest {

    public void runAll() throws Exception {
        serviceLoaderDiscoversPlugins();
        rateLimitPluginCanShortCircuitRequests();
        schedulerPluginRegistersAndRunsTasks();
    }

    public void serviceLoaderDiscoversPlugins() {
        RestHttpServer server = new RestHttpServer() {};
        PluginContext context = ModuleRegistry.loadPlugins(server);
        SchedulerModule scheduler = context.getModule(SchedulerModule.class);

        TestSupport.assertTrue(context.getModule(GraphQLModule.class) != null, "ServiceLoader should discover GraphQL plugin");
        TestSupport.assertTrue(context.getModule(RateLimitModule.class) != null, "ServiceLoader should discover rate limit plugin");
        TestSupport.assertTrue(scheduler != null, "ServiceLoader should discover scheduler plugin");
        scheduler.shutdown();
    }

    public void rateLimitPluginCanShortCircuitRequests() throws Exception {
        RestHttpServer server = new RestHttpServer() {};
        ModuleRegistry.initialize(server, new RateLimitModuleService());
        server.addEndpoint("/plugin-test", new LimitedEndpoint());

        RestHttpRequest firstRequest = request("/plugin-test/limited", "127.0.0.1");
        RestHttpRequest secondRequest = request("/plugin-test/limited", "127.0.0.1");

        Response firstResponse = handle(server, firstRequest);
        Response secondResponse = handle(server, secondRequest);

        TestSupport.assertEquals("ok", bodyFromResponse(firstResponse), "First request should pass through");
        TestSupport.assertTrue(new String(secondResponse.build()).startsWith("HTTP/1.1 429"), "Second request should be rate limited");
    }

    public void schedulerPluginRegistersAndRunsTasks() throws Exception {
        RestHttpServer server = new RestHttpServer() {};
        SchedulerModuleService schedulerPlugin = new SchedulerModuleService();
        PluginContext context = ModuleRegistry.initialize(server, schedulerPlugin);
        SchedulerModule scheduler = context.getModule(SchedulerModule.class);
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            scheduler.schedule("test-task", new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }, 0, 100, TimeUnit.MILLISECONDS);

            TestSupport.assertTrue(latch.await(1, TimeUnit.SECONDS), "Scheduled task should run");
        } finally {
            scheduler.shutdown();
        }
    }

    private RestHttpRequest request(String path, String forwardedFor) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Forwarded-For", forwardedFor);
        return new RestHttpRequest(headers, new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>(), null, "GET", path, "");
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

    private String bodyFromResponse(Response response) {
        String raw = new String(response.build());
        int split = raw.indexOf("\r\n\r\n");
        if (split == -1) {
            return "";
        }
        return raw.substring(split + 4);
    }

    public static class LimitedEndpoint {
        @Action(path = "/limited", consume = "*/*")
        @RateLimitModule.RateLimit(requests = 1, perSeconds = 60)
        public Response limited() {
            return new Response().OK().ContentType("text/plain").data("ok");
        }
    }
}
