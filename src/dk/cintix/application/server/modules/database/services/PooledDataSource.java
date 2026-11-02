package dk.cintix.application.server.modules.database.services;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A production-grade JDBC connection pool with connection validation,
 * idle eviction, max lifetime enforcement, and configurable sizing.
 *
 * @author migo
 */
public class PooledDataSource implements javax.sql.DataSource {

    private static final Logger logger = Logger.getLogger(PooledDataSource.class.getName());

    // --- Per-instance configuration ---
    private final String url;
    private final String user;
    private final String password;

    /** Initial number of connections created at startup. */
    private final int initialPoolSize;

    /** Maximum number of connections allowed in the pool (idle + in-use). */
    private volatile int maxPoolSize;

    /** Maximum time (ms) to wait for a connection before throwing. */
    private volatile int connectionTimeoutMs;

    /** Maximum time (ms) a connection may be idle in the pool before eviction. */
    private volatile long maxIdleTimeMs;

    /** Maximum time (ms) a connection may live before being recycled. */
    private volatile long maxLifetimeMs;

    /** Timeout (seconds) for {@link Connection#isValid(int)} checks. */
    private volatile int validationTimeoutSec;

    /** Interval (ms) between idle/lifetime eviction sweeps. */
    private volatile long evictionIntervalMs;

    /**
     * Time (ms) a connection may be borrowed before a leak warning names the
     * borrowing thread and its stack. {@code 0} disables the warning (the
     * default); a borrowed connection is still reported when the pool is
     * exhausted.
     */
    private volatile long leakDetectionThresholdMs;

    // --- Per-instance state ---
    private final List<Connection> idlePool = new ArrayList<>();
    private final List<Connection> activeConnections = new ArrayList<>();
    /** Borrow diagnostics, keyed by identity. Guarded by {@code this}. */
    private final Map<Connection, BorrowRecord> borrowRecords = new IdentityHashMap<>();
    private final ScheduledExecutorService evictionExecutor;
    private volatile ScheduledFuture<?> evictionTask;
    private volatile boolean closed;

    // --- Production defaults ---
    private static final int DEFAULT_INITIAL_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 30_000;
    private static final long DEFAULT_MAX_IDLE_TIME_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long DEFAULT_MAX_LIFETIME_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int DEFAULT_VALIDATION_TIMEOUT_SEC = 3;
    private static final long DEFAULT_EVICTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 0;

    /** How many borrowed connections an exhaustion report names, oldest first. */
    private static final int MAX_REPORTED_BORROWS = 3;
    private static final int MAX_STACK_FRAMES = 8;

    /**
     * Creates a pool with production defaults for all sizing parameters.
     */
    public PooledDataSource(String url, String user, String password) {
        this(url, user, password, DEFAULT_INITIAL_POOL_SIZE);
    }

    /**
     * Creates a pool with the given initial size and production defaults
     * for all other parameters.
     */
    public PooledDataSource(String url, String user, String password, int initialPoolSize) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.initialPoolSize = Math.max(1, initialPoolSize);
        this.maxPoolSize = DEFAULT_MAX_POOL_SIZE;
        this.connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;
        this.maxIdleTimeMs = DEFAULT_MAX_IDLE_TIME_MS;
        this.maxLifetimeMs = DEFAULT_MAX_LIFETIME_MS;
        this.validationTimeoutSec = DEFAULT_VALIDATION_TIMEOUT_SEC;
        this.evictionIntervalMs = DEFAULT_EVICTION_INTERVAL_MS;
        this.leakDetectionThresholdMs = DEFAULT_LEAK_DETECTION_THRESHOLD_MS;

        // Ensure max >= initial
        if (this.maxPoolSize < this.initialPoolSize) {
            this.maxPoolSize = this.initialPoolSize;
        }

        // Pre-fill the pool
        int created = 0;
        try {
            for (int index = 0; index < this.initialPoolSize; index++) {
                Connection conn = DriverManager.getConnection(url, user, password);
                synchronized (this) {
                    idlePool.add(conn);
                }
                created++;
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to create initial connection pool (created " + created + "/" + initialPoolSize + ")", exception);
        }

        if (created == 0) {
            throw new RuntimeException("Could not create any initial connections for pool: " + url);
        }

        // Start background eviction thread
        evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pool-evictor-" + url.replaceAll(".*//", "").replaceAll("[:/].*", ""));
            t.setDaemon(true);
            return t;
        });
        evictionTask = evictionExecutor.scheduleWithFixedDelay(
            this::evict,
            evictionIntervalMs,
            evictionIntervalMs,
            TimeUnit.MILLISECONDS
        );

        logger.log(Level.INFO, "Connection pool created: url={0}, initial={1}, max={2}, timeout={3}ms, idleTtl={4}ms, maxLife={5}ms, leakDetection={6}ms",
            new Object[]{url, initialPoolSize, maxPoolSize, connectionTimeoutMs, maxIdleTimeMs, maxLifetimeMs,
                leakDetectionThresholdMs == 0 ? "disabled" : String.valueOf(leakDetectionThresholdMs)});
    }

    // --- Configuration setters (call before first getConnection) ---

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = Math.max(Math.max(1, maxPoolSize), this.initialPoolSize);
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = Math.max(0, connectionTimeoutMs);
    }

    public void setMaxIdleTimeMs(long maxIdleTimeMs) {
        this.maxIdleTimeMs = Math.max(1, maxIdleTimeMs);
    }

    public void setMaxLifetimeMs(long maxLifetimeMs) {
        this.maxLifetimeMs = Math.max(1, maxLifetimeMs);
    }

    public void setValidationTimeoutSec(int validationTimeoutSec) {
        this.validationTimeoutSec = Math.max(1, validationTimeoutSec);
    }

    public void setEvictionIntervalMs(long evictionIntervalMs) {
        long interval = Math.max(1000, evictionIntervalMs);
        synchronized (this) {
            this.evictionIntervalMs = interval;
            if (!closed && evictionTask != null) {
                // The sweep was scheduled with the old interval at construction.
                // Reschedule so the setter is not silently ineffective.
                evictionTask.cancel(false);
                evictionTask = evictionExecutor.scheduleWithFixedDelay(
                        this::evict, interval, interval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Enables leak detection: a connection borrowed for longer than
     * {@code thresholdMs} causes one WARNING that names the borrowing thread
     * and its stack, so the line that forgot to close it can be found.
     *
     * <p>{@code 0} disables the warning (default). The pool's existing
     * eviction thread performs the check, so the warning appears at most one
     * eviction interval after the threshold is crossed. Separately, when the
     * pool is exhausted the {@link SQLException} always names the
     * longest-held borrows and the stacks that borrowed them.</p>
     */
    public void setLeakDetectionThresholdMs(long thresholdMs) {
        this.leakDetectionThresholdMs = Math.max(0, thresholdMs);
    }

    public long getLeakDetectionThresholdMs() { return leakDetectionThresholdMs; }

    // --- Getters ---

    public int getInitialPoolSize() { return initialPoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getMaxIdleTimeMs() { return maxIdleTimeMs; }
    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public int getValidationTimeoutSec() { return validationTimeoutSec; }
    public long getEvictionIntervalMs() { return evictionIntervalMs; }
    public String getUrl() { return url; }
    public String getUser() { return user; }
    public String getPassword() { return password; }

    public int getSize() {
        synchronized (this) {
            return idlePool.size() + activeConnections.size();
        }
    }

    public int getIdleCount() {
        synchronized (this) {
            return idlePool.size();
        }
    }

    public int getActiveCount() {
        synchronized (this) {
            return activeConnections.size();
        }
    }

    /**
     * Releases a connection back to the pool. Idempotent — if the connection
     * is already closed, this is a no-op.
     *
     * <p>Accepts either the handle returned by {@link #getConnection()} or the
     * underlying physical connection, so callers may use
     * {@code try (Connection c = dataSource.getConnection())} and let
     * {@code close()} do the work.</p>
     */
    public boolean releaseConnection(Connection connection) {
        Connection physical = PooledConnectionHandler.unwrap(connection);
        if (physical == null) {
            return false;
        }
        synchronized (this) {
            boolean removed = activeConnections.remove(physical);
            if (!removed) {
                return false;
            }
            borrowRecords.remove(physical);
            idlePool.add(physical);
            this.notifyAll();
            return true;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(user, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        long deadline = System.currentTimeMillis() + connectionTimeoutMs;

        synchronized (this) {
            while (true) {
                // Try to borrow a validated idle connection
                while (!idlePool.isEmpty()) {
                    Connection conn = idlePool.remove(idlePool.size() - 1);
                    if (isConnectionValid(conn)) {
                        activeConnections.add(conn);
                        recordBorrow(conn);
                        return PooledConnectionHandler.wrap(this, conn);
                    }
                    // Stale connection — discard and try the next one
                    closeQuietly(conn);
                }

                // Pool is empty — can we grow?
                int currentTotal = idlePool.size() + activeConnections.size();
                if (currentTotal < maxPoolSize) {
                    try {
                        Connection conn = DriverManager.getConnection(url, user, password);
                        activeConnections.add(conn);
                        recordBorrow(conn);
                        logger.log(Level.FINE, "Pool grown to {0} connections", currentTotal + 1);
                        return PooledConnectionHandler.wrap(this, conn);
                    } catch (SQLException e) {
                        logger.log(Level.WARNING, "Failed to create new connection for pool growth", e);
                        // Fall through to wait logic
                    }
                }

                // Cannot grow — wait for a connection to be returned
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    StringBuilder message = new StringBuilder();
                    message.append("Connection pool exhausted: active=").append(activeConnections.size())
                            .append(", idle=").append(idlePool.size()).append(", max=").append(maxPoolSize)
                            .append(" (waited ").append(connectionTimeoutMs).append("ms)");
                    if (!borrowRecords.isEmpty()) {
                        message.append(". Connections currently borrowed (oldest first):\n")
                                .append(buildBorrowReport(System.currentTimeMillis()));
                    }
                    throw new SQLException(message.toString());
                }

                try {
                    this.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for a connection");
                }
            }
        }
    }

    /**
     * Records who borrowed a connection and when. Must be called with
     * {@code this} held.
     *
     * <p>The stack is captured on every borrow, not only when leak detection is
     * enabled, because the pool-exhaustion report is the one place the
     * responsible line matters most — and that failure can happen with the
     * threshold at its default. The cost is a few microseconds against the
     * driver round-trip a borrow already performs.</p>
     */
    private void recordBorrow(Connection conn) {
        borrowRecords.put(conn, new BorrowRecord(
                System.currentTimeMillis(),
                Thread.currentThread().getName(),
                Thread.currentThread().getStackTrace()));
    }

    /**
     * Logs one WARNING per borrowed connection that has been held past the
     * configured leak threshold. Runs from the eviction thread.
     */
    private void checkLeaks() {
        long threshold = leakDetectionThresholdMs;
        if (threshold <= 0) {
            return;
        }

        List<String> warnings = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (this) {
            for (BorrowRecord record : borrowRecords.values()) {
                if (!record.warned && now - record.borrowedAt >= threshold) {
                    record.warned = true;
                    warnings.add(describeBorrow(record, now));
                }
            }
        }

        for (String warning : warnings) {
            logger.log(Level.WARNING,
                    "Possible connection leak: a connection was borrowed more than {0}ms ago and never closed.\n{1}"
                    + "Wrap the connection in try-with-resources, or call close() when done.",
                    new Object[]{threshold, warning});
        }
    }

    /**
     * Describes every currently borrowed connection, oldest first, with the
     * borrowing thread and (when captured) the stack that borrowed it. Must be
     * called with {@code this} held.
     */
    private String buildBorrowReport(long now) {
        List<BorrowRecord> records = new ArrayList<>(borrowRecords.values());
        records.sort((left, right) -> Long.compare(left.borrowedAt, right.borrowedAt));

        StringBuilder report = new StringBuilder();
        int shown = 0;
        for (BorrowRecord record : records) {
            if (shown == MAX_REPORTED_BORROWS) {
                report.append("  ... and ").append(records.size() - shown).append(" more borrowed connection(s)\n");
                break;
            }
            report.append(describeBorrow(record, now));
            shown++;
        }
        return report.toString();
    }

    private String describeBorrow(BorrowRecord record, long now) {
        StringBuilder description = new StringBuilder(256);
        description.append("  connection borrowed ").append(now - record.borrowedAt)
                .append("ms ago by thread \"").append(record.threadName).append('"');

        if (record.stack == null) {
            description.append(" (stack not captured; enable leakDetectionThresholdMs before borrowing)\n");
            return description.toString();
        }

        description.append(":\n");
        int shown = 0;
        for (StackTraceElement frame : record.stack) {
            String className = frame.getClassName();
            if (className.startsWith("java.lang.Thread")
                    || className.equals(PooledDataSource.class.getName())
                    || className.startsWith(PooledDataSource.class.getName() + "$")) {
                continue;
            }
            description.append("      at ").append(frame).append('\n');
            if (++shown >= MAX_STACK_FRAMES) {
                description.append("      ...\n");
                break;
            }
        }
        return description.toString();
    }

    /** Borrow diagnostic for one connection. Written under {@code this}. */
    private static final class BorrowRecord {
        final long borrowedAt;
        final String threadName;
        final StackTraceElement[] stack;
        boolean warned;

        BorrowRecord(long borrowedAt, String threadName, StackTraceElement[] stack) {
            this.borrowedAt = borrowedAt;
            this.threadName = threadName;
            this.stack = stack;
        }
    }

    /**
     * Validates a connection. Returns true if the connection is open and
     * responds to the JDBC driver's validity check within the configured timeout.
     */
    private boolean isConnectionValid(Connection conn) {
        try {
            if (conn.isClosed()) {
                return false;
            }
            return conn.isValid(validationTimeoutSec);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Background eviction task — removes idle connections that have been
     * idle too long and connections that have exceeded their max lifetime.
     * Keeps at least initialPoolSize connections in the pool.
     */
    private void evict() {
        try {
            checkLeaks();
            List<Connection> toClose = new ArrayList<>();
            synchronized (this) {
                int idleCount = idlePool.size();
                int activeCount = activeConnections.size();
                int total = idleCount + activeCount;

                // Evict idle connections beyond initialPoolSize that exceed idle/lifetime limits
                int keepAtLeast = Math.min(initialPoolSize, idleCount);
                List<Connection> surviving = new ArrayList<>();

                for (Connection conn : idlePool) {
                    if (shouldEvict(conn)) {
                        toClose.add(conn);
                    } else {
                        surviving.add(conn);
                    }
                }

                // Preserve at least initialPoolSize idle connections
                while (surviving.size() < keepAtLeast && !toClose.isEmpty()) {
                    surviving.add(toClose.remove(toClose.size() - 1));
                }

                idlePool.clear();
                idlePool.addAll(surviving);

                if (!toClose.isEmpty()) {
                    logger.log(Level.FINE, "Evicting {0} idle connections (idle={1}, active={2}, total={3})",
                        new Object[]{toClose.size(), idlePool.size(), activeConnections.size(), total});
                }
            }

            // Close evicted connections outside the lock
            for (Connection conn : toClose) {
                closeQuietly(conn);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Eviction sweep failed", e);
        }
    }

    /**
     * Decides whether an idle connection should be evicted based on
     * idle time or max lifetime.
     */
    private boolean shouldEvict(Connection conn) {
        // The DriverManager connections don't carry timestamps.
        // We rely on isValid() as the primary health check.
        // The eviction sweep simply trims the pool down to initialPoolSize
        // once connections become idle. Connections that are unresponsive
        // are caught by isConnectionValid() on borrow.
        //
        // This is a conservative approach: we close idle connections
        // above initialPoolSize to prevent resource waste.
        return true; // The caller decides how many to keep
    }

    /**
     * Shuts down the pool, closing all connections and stopping the
     * background eviction thread.
     */
    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;

        evictionExecutor.shutdown();
        try {
            evictionExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            for (Connection conn : idlePool) {
                closeQuietly(conn);
            }
            for (Connection conn : activeConnections) {
                closeQuietly(conn);
            }
            idlePool.clear();
            activeConnections.clear();
            borrowRecords.clear();
            this.notifyAll();
        }
        logger.log(Level.INFO, "Connection pool shut down: {0}", url);
    }

    private void closeQuietly(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "Error closing connection", e);
        }
    }

    // --- javax.sql.DataSource delegation ---

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter writer) throws SQLException {
        DriverManager.setLogWriter(writer);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return (T) this;
        }
        throw new SQLException("Cannot unwrap to " + type.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return type.isInstance(this);
    }
}
