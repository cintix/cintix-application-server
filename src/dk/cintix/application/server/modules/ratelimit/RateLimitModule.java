package dk.cintix.application.server.modules.ratelimit;

import dk.cintix.application.server.infrastructure.modules.Plugin;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Public contract for the rate limit plugin module.
 *
 * @author cix
 */
public interface RateLimitModule extends Plugin {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public static @interface RateLimit {
        /**
         * Maximum number of requests allowed in the window.
         * Use 0 to disable rate limiting for this endpoint (whitelist).
         */
        int requests();
        /** Window size in seconds. */
        int perSeconds();
        /** Header used to identify the client (default: X-Forwarded-For). */
        String keyHeader() default "X-Forwarded-For";
    }
}
