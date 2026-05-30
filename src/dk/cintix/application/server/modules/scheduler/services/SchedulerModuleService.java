package dk.cintix.application.server.modules.scheduler.services;

import dk.cintix.application.server.infrastructure.modules.PluginContext;
import dk.cintix.application.server.modules.scheduler.SchedulerModule;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade service for the scheduler plugin.
 *
 * @author cix
 */
public class SchedulerModuleService implements SchedulerModule {

    private static final Logger logger = Logger.getLogger(SchedulerModuleService.class.getName());
    private final ScheduledExecutorService executor;

    public SchedulerModuleService() {
        this(Executors.newScheduledThreadPool(1));
    }

    public SchedulerModuleService(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public String getName() {
        return "scheduler";
    }

    @Override
    public void register(PluginContext context) {
        context.registerModule(SchedulerModule.class, this);
    }

    @Override
    public void registerJob(Object job) {
        Method[] methods = job.getClass().getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.isAnnotationPresent(Scheduled.class)) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                schedule(method, job, scheduled);
            }
        }
    }

    @Override
    public void schedule(String name, Runnable task, long initialDelay, long period, TimeUnit unit) {
        executor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    private void schedule(final Method method, final Object job, Scheduled scheduled) {
        if (method.getParameterTypes().length != 0) {
            throw new IllegalArgumentException("Scheduled method must not have parameters: " + method.toString());
        }
        method.setAccessible(true);
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    method.invoke(job);
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Scheduled job invocation failed: " + method.getName(), exception);
                }
            }
        }, scheduled.initialDelaySeconds(), scheduled.fixedRateSeconds(), TimeUnit.SECONDS);
    }
}
