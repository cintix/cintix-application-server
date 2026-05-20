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
        int requests();
        int perSeconds();
        String keyHeader() default "X-Forwarded-For";
    }
}
