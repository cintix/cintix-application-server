package dk.cintix.application.server.infrastructure.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the maximum processing time (in milliseconds) for a request.
 * Overrides the server-wide default request timeout.
 *
 * <p>Use 0 to disable the timeout for this endpoint (e.g. long-running
 * CSV exports or streaming responses).</p>
 *
 * <pre>{@code
 *   @Timeout(ms = 120_000)  // 2 minutes for large exports
 *   public Response exportCsv() { ... }
 *
 *   @Timeout(ms = 0)        // no timeout for WebSocket-like endpoints
 *   public Response stream() { ... }
 * }</pre>
 *
 * @author cix
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Timeout {
    /** Timeout in milliseconds. 0 means no timeout. */
    int ms();
}
