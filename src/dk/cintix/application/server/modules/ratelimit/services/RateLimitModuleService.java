package dk.cintix.application.server.modules.ratelimit.services;

import dk.cintix.application.server.infrastructure.modules.PluginContext;
import dk.cintix.application.server.modules.http.server.HttpModule;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.ratelimit.RateLimitModule;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opt-in rate limiting with global defaults and per-endpoint overrides.
 *
 * <h3>Two-level configuration</h3>
 * <ol>
 *   <li><b>Global default</b> — when {@link #setEnabled(boolean) enabled},
 *       all endpoints are rate-limited using {@link #setDefaultRequests(int)}
 *       and {@link #setDefaultPerSeconds(int)} (defaults: 100 req / 60s).</li>
 *   <li><b>Per-endpoint override</b> — the {@code @RateLimit} annotation
 *       on a method or class overrides the global setting for that endpoint.
 *       Use {@code requests = 0} to whitelist an endpoint (no limit).</li>
 * </ol>
 *
 * <p>Rate limiting is <b>off by default</b>. Applications opt in by calling
 * {@code setEnabled(true)}.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   RateLimitModuleService rateLimit = new RateLimitModuleService();
 *   rateLimit.setEnabled(true);
 *   rateLimit.setDefaultRequests(200);
 *   rateLimit.setDefaultPerSeconds(60);
 *   ModuleRegistry.initialize(server, rateLimit);
 *
 *   // Per-endpoint overrides:
 *   @RateLimit(requests = 10, perSeconds = 60)   // stricter
 *   @RateLimit(requests = 500, perSeconds = 60)  // more lenient
 *   @RateLimit(requests = 0, perSeconds = 1)     // whitelist (no limit)
 * }</pre>
 *
 * @author cix
 */
public class RateLimitModuleService implements RateLimitModule {

    private static final Logger logger = Logger.getLogger(RateLimitModuleService.class.getName());

    // --- Global configuration ---
    private volatile boolean enabled;
    private volatile int defaultRequests = 100;
    private volatile int defaultPerSeconds = 60;
    private volatile String defaultKeyHeader = "X-Forwarded-For";

    // --- Internal state ---
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private static final long CLEANUP_INTERVAL_SEC = 60;
    private static final int MAX_WINDOWS = 10_000;

    public RateLimitModuleService() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupExpiredWindows,
            CLEANUP_INTERVAL_SEC,
            CLEANUP_INTERVAL_SEC,
            TimeUnit.SECONDS
        );
    }

    // --- Configuration ---

    /** Enables or disables global rate limiting (off by default). */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    /** Default max requests per window when no {@code @RateLimit} annotation is present. */
    public void setDefaultRequests(int requests) { this.defaultRequests = Math.max(1, requests); }
    public int getDefaultRequests() { return defaultRequests; }

    /** Default window size in seconds. */
    public void setDefaultPerSeconds(int seconds) { this.defaultPerSeconds = Math.max(1, seconds); }
    public int getDefaultPerSeconds() { return defaultPerSeconds; }

    /** Default header used to identify clients. */
    public void setDefaultKeyHeader(String header) { this.defaultKeyHeader = header; }
    public String getDefaultKeyHeader() { return defaultKeyHeader; }

    // --- Plugin lifecycle ---

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

    /** Shuts down the background cleanup thread. */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        windows.clear();
    }

    // --- Internal ---

    private Response apply(RestHttpRequest request, HttpModule.EndpointInfo endpoint) {
        // Resolve effective rate limit: annotation overrides global
        int maxRequests;
        int perSeconds;
        String keyHeader;

        RateLimit annotation = endpoint.getAnnotation(RateLimit.class);
        if (annotation != null) {
            // requests = 0 means "whitelist" — disable rate limiting for this endpoint
            if (annotation.requests() == 0) {
                return null;
            }
            maxRequests = annotation.requests();
            perSeconds = annotation.perSeconds();
            keyHeader = annotation.keyHeader();
        } else if (enabled) {
            // Use global defaults
            maxRequests = defaultRequests;
            perSeconds = defaultPerSeconds;
            keyHeader = defaultKeyHeader;
        } else {
            // Rate limiting disabled globally and no annotation — pass through
            return null;
        }

        String clientKey = resolveClientKey(request, keyHeader);
        String windowKey = endpoint.getPath() + ":" + request.getMethod() + ":" + clientKey;

        Window window = windows.computeIfAbsent(windowKey, k -> new Window());

        synchronized (window) {
            long now = System.currentTimeMillis();
            long windowMs = perSeconds * 1000L;

            if (window.startedAt == 0 || now - window.startedAt >= windowMs) {
                window.startedAt = now;
                window.requests = 0;
            }

            window.requests++;

            if (window.requests > maxRequests) {
                long retryAfterSec = Math.max(1,
                    (windowMs - (now - window.startedAt)) / 1000L + 1);
                logger.log(Level.FINE,
                    "Rate limit hit: key={0}, requests={1}, limit={2}/{3}s",
                    new Object[]{windowKey, window.requests, maxRequests, perSeconds});
                return new Response()
                    .TooManyRequests()
                    .ContentType("text/plain")
                    .header("Retry-After", Long.toString(retryAfterSec))
                    .data("Rate limit exceeded");
            }
        }

        return null;
    }

    private static String resolveClientKey(RestHttpRequest request, String keyHeader) {
        String value = request.getHeader(keyHeader);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return "anonymous";
    }

    private void cleanupExpiredWindows() {
        try {
            int sizeBefore = windows.size();
            if (sizeBefore > MAX_WINDOWS) {
                long threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
                Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Window> entry = it.next();
                    synchronized (entry.getValue()) {
                        if (entry.getValue().startedAt < threshold) {
                            it.remove();
                        }
                    }
                }
                if (windows.size() < sizeBefore) {
                    logger.log(Level.INFO, "Aggressive rate-limit cleanup: {0} → {1} windows",
                        new Object[]{sizeBefore, windows.size()});
                }
            } else {
                Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Window> entry = it.next();
                    synchronized (entry.getValue()) {
                        long age = System.currentTimeMillis() - entry.getValue().startedAt;
                        if (age > TimeUnit.MINUTES.toMillis(10)) {
                            it.remove();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Rate-limit cleanup failed", e);
        }
    }

    private static class Window {
        volatile long startedAt;
        volatile int requests;
    }
}
