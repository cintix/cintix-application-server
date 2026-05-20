package dk.cintix.application.server.modules.scheduler;

import dk.cintix.application.server.infrastructure.modules.Plugin;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Public contract for the scheduler plugin module.
 *
 * @author cix
 */
public interface SchedulerModule extends Plugin {
    void registerJob(Object job);
    void schedule(String name, Runnable task, long initialDelay, long period, TimeUnit unit);
    void shutdown();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Scheduled {
        String name() default "";
        long initialDelaySeconds() default 0;
        long fixedRateSeconds();
    }
}
