package dk.cintix.application.server.modules.ratelimit.services;

import dk.cintix.application.server.infrastructure.modules.PluginContext;
import dk.cintix.application.server.modules.http.server.HttpModule;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.ratelimit.RateLimitModule;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade service for the rate limit plugin.
 *
 * @author cix
 */
public class RateLimitModuleService implements RateLimitModule {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "rate-limit";
    }

    @Override
    public void register(PluginContext context) {
        context.registerModule(RateLimitModule.class, this);
        context.getHttpModule().addRequestFilter(new HttpModule.RequestFilter() {
            @Override
            public Response filter(RestHttpRequest request, HttpModule.EndpointInfo endpoint) {
                return apply(request, endpoint);
            }
        });
    }

    private Response apply(RestHttpRequest request, HttpModule.EndpointInfo endpoint) {
        RateLimit rateLimit = endpoint.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return null;
        }

        String key = request.getHeader(rateLimit.keyHeader());
        if (key == null || key.trim().isEmpty()) {
            key = "global";
        }

        String windowKey = endpoint.getPath() + ":" + request.getMethod() + ":" + key;
        Window window = windows.get(windowKey);
        if (window == null) {
            window = new Window();
            windows.put(windowKey, window);
        }

        synchronized (window) {
            long now = System.currentTimeMillis();
            long windowLength = rateLimit.perSeconds() * 1000L;
            if (window.startedAt == 0 || now - window.startedAt >= windowLength) {
                window.startedAt = now;
                window.requests = 0;
            }

            window.requests++;
            if (window.requests > rateLimit.requests()) {
                long retryAfter = Math.max(1L, (windowLength - (now - window.startedAt) + 999L) / 1000L);
                return new Response()
                        .TooManyRequests()
                        .ContentType("text/plain")
                        .header("Retry-After", Long.toString(retryAfter))
                        .data("Rate limit exceeded");
            }
        }

        return null;
    }

    private static class Window {
        private long startedAt;
        private int requests;
    }
}
