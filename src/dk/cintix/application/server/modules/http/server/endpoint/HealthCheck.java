package dk.cintix.application.server.modules.http.server.endpoint;

import java.util.Map;

/**
 * A pluggable health probe that reports its status.
 * Implementations must be fast and non-blocking — health checks run
 * inline on the NIO event loop and bypass the worker pool.
 *
 * @author cix
 */
public interface HealthCheck {

    /** Unique name for this probe (e.g. "database", "disk"). */
    String name();

    /**
     * Performs the health check. Must return quickly (no blocking I/O).
     *
     * @return status string — "UP" if healthy, any other string describes the problem
     */
    String check();
}
