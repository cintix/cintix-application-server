package dk.cintix.application.server.modules.database.services;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The logical connection handle handed out by {@link PooledDataSource}.
 *
 * <p>JDBC says a {@code Connection} obtained from a pooled {@code DataSource} is
 * a handle whose {@code close()} returns it to the pool rather than shutting the
 * physical connection down. Handing out the physical connection instead made
 * the idiomatic form</p>
 *
 * <pre>{@code try (Connection c = dataSource.getConnection()) { ... }}</pre>
 *
 * <p>close the socket while leaving the reference in the pool's active list
 * forever — so the pool filled up with dead entries and every later lookup
 * failed with {@code Connection pool exhausted} after the full timeout, at a
 * call site that had done nothing wrong.</p>
 *
 * <p>The handle is single-use: once closed, any further call throws rather than
 * quietly operating on a connection that may already belong to another thread.</p>
 *
 * @author cix
 */
class PooledConnectionHandler implements InvocationHandler {

    private final PooledDataSource dataSource;
    private final Connection physical;

    private boolean closed;

    private PooledConnectionHandler(PooledDataSource dataSource, Connection physical) {
        this.dataSource = dataSource;
        this.physical = physical;
    }

    /**
     * Wraps a physical connection in a handle that returns it to {@code dataSource}
     * when closed.
     */
    static Connection wrap(PooledDataSource dataSource, Connection physical) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                new PooledConnectionHandler(dataSource, physical));
    }

    /**
     * Resolves a handle created by this pool back to the physical connection it
     * wraps. Anything else — including proxies from another source — is returned
     * untouched, so {@link PooledDataSource#releaseConnection(Connection)}
     * accepts both forms.
     */
    static Connection unwrap(Connection connection) {
        if (connection != null && Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            if (handler instanceof PooledConnectionHandler) {
                return ((PooledConnectionHandler) handler).physical;
            }
        }
        return connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("close".equals(name)) {
            return closeHandle();
        }
        if ("isClosed".equals(name)) {
            return closed || physical.isClosed();
        }
        if ("equals".equals(name)) {
            return proxy == (args != null && args.length == 1 ? args[0] : null);
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(name)) {
            return "PooledConnection[" + physical + "]";
        }

        if (closed) {
            throw new SQLException(
                    "Connection handle is closed — it has already been returned to the pool");
        }

        try {
            return method.invoke(physical, args);
        } catch (InvocationTargetException e) {
            // Surface the driver's own exception, not the reflection wrapper.
            throw e.getCause();
        }
    }

    /**
     * Returns the connection to the pool exactly once.
     *
     * <p>Deliberately does not close the physical connection when the release
     * fails: a failed release means the pool no longer considers this connection
     * active — it may already be idle and handed to another thread, and closing
     * it here would pull it out from under them.</p>
     */
    private synchronized Object closeHandle() {
        if (closed) {
            return null;
        }
        closed = true;
        dataSource.releaseConnection(physical);
        return null;
    }

}
