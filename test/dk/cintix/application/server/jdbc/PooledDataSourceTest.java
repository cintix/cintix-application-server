package dk.cintix.application.server.jdbc;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.database.services.PooledDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class PooledDataSourceTest {

    private static final Logger POOL_LOGGER = Logger.getLogger(PooledDataSource.class.getName());

    public void runAll() throws Exception {
        getConnectionAndReleaseConnection_updatePoolState();
        invalidConnection_detectedOnBorrow();
        tryWithResources_returnsConnectionToPool();
        tryWithResources_reusesOnePhysicalConnection();
        closeIsIdempotent_releasesOnlyOnce();
        closedHandle_rejectsFurtherUse();
        releaseConnection_acceptsHandleAndPhysical();
        poolExhaustion_reportsBorrowingThreadAndStack();
        leakDetection_warnsWithBorrowingStack();
    }

    public void tryWithResources_returnsConnectionToPool() throws Exception {
        // Arrange
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:twr", "u", "p", 1);
        dataSource.setMaxPoolSize(3);
        dataSource.setConnectionTimeoutMs(500);

        // Act — the idiomatic JDBC form, more times than the pool has connections
        for (int i = 0; i < 5; i++) {
            try (Connection connection = dataSource.getConnection()) {
                // Assert — the handle is open inside its own block
                TestSupport.assertFalse(connection.isClosed(),
                        "Handle should be open inside the try block (iteration " + i + ")");
            }
        }

        // Assert — every lookup handed its connection back
        TestSupport.assertEquals(0, dataSource.getActiveCount(),
                "try-with-resources must return the connection to the pool, not just close the socket");
        dataSource.shutdown();
    }

    public void tryWithResources_reusesOnePhysicalConnection() throws Exception {
        // Arrange — a pool of one, so any leak shows up immediately
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:reuse", "u", "p", 1);
        dataSource.setMaxPoolSize(3);
        dataSource.setConnectionTimeoutMs(500);

        // Act — three sequential blocks
        for (int i = 0; i < 3; i++) {
            try (Connection connection = dataSource.getConnection()) {
                TestSupport.assertTrue(connection != null, "Expected a connection");
            }
        }

        // Assert — one physical connection served all three, and it is idle now
        TestSupport.assertEquals(1, MockJdbc.createdStates.size(),
                "Pool should reuse its one connection, but created " + MockJdbc.createdStates.size());
        TestSupport.assertEquals(0, dataSource.getActiveCount(), "No connection may stay active");
        TestSupport.assertEquals(1, dataSource.getIdleCount(), "The connection should be idle again");

        // The lookup that used to fail with "Connection pool exhausted"
        try (Connection connection = dataSource.getConnection()) {
            TestSupport.assertTrue(connection != null, "Pool must still hand out connections");
        }
        dataSource.shutdown();
    }

    public void closeIsIdempotent_releasesOnlyOnce() throws Exception {
        // Arrange
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:idem", "u", "p", 1);

        // Act — a caller that closes twice must not put two copies in the pool
        Connection connection = dataSource.getConnection();
        connection.close();
        connection.close();

        // Assert
        TestSupport.assertEquals(1, dataSource.getIdleCount(),
                "Double close must not duplicate the connection in the idle pool");
        TestSupport.assertEquals(0, dataSource.getActiveCount(), "Nothing should remain active");
        dataSource.shutdown();
    }

    public void closedHandle_rejectsFurtherUse() throws Exception {
        // Arrange — a connection that has gone back to the pool may already
        // belong to another thread, so the stale handle must not work
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:stale", "u", "p", 1);

        Connection connection = dataSource.getConnection();
        connection.close();

        // Act
        boolean rejected;
        try {
            connection.createStatement();
            rejected = false;
        } catch (java.sql.SQLException e) {
            rejected = true;
        }

        // Assert
        TestSupport.assertTrue(rejected, "A closed handle must refuse further use");
        TestSupport.assertTrue(connection.isClosed(), "isClosed must report the handle as closed");
        dataSource.shutdown();
    }

    public void releaseConnection_acceptsHandleAndPhysical() throws Exception {
        // Arrange — releaseConnection is public, so both forms must keep working
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:both", "u", "p", 1);

        // Act — the handle, as returned by getConnection()
        Connection handle = dataSource.getConnection();
        boolean releasedHandle = dataSource.releaseConnection(handle);

        // Assert
        TestSupport.assertTrue(releasedHandle, "releaseConnection must accept the handle");
        TestSupport.assertEquals(0, dataSource.getActiveCount(), "Handle release left it active");

        // Act — and a second release of the same handle is a harmless no-op
        boolean releasedAgain = dataSource.releaseConnection(handle);

        // Assert
        TestSupport.assertFalse(releasedAgain, "Re-releasing must report failure, not double-add");
        TestSupport.assertEquals(1, dataSource.getIdleCount(), "Idle pool must not gain a duplicate");
        dataSource.shutdown();
    }

    public void poolExhaustion_reportsBorrowingThreadAndStack() throws Exception {
        // Arrange — a pool of one whose only connection is never returned
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:leak-report", "u", "p", 1);
        dataSource.setMaxPoolSize(1);
        dataSource.setConnectionTimeoutMs(150);
        Connection held = dataSource.getConnection();

        // Act — the next borrower times out
        String message = null;
        try {
            dataSource.getConnection();
        } catch (SQLException e) {
            message = e.getMessage();
        }

        // Assert — the failure names who is holding the connection and where
        TestSupport.assertTrue(message != null, "Expected the second borrow to time out");
        TestSupport.assertTrue(message.contains("Connection pool exhausted"),
                "Expected the exhaustion message, got: " + message);
        TestSupport.assertTrue(message.contains("Connections currently borrowed"),
                "Exhaustion must report the outstanding borrows, got: " + message);
        TestSupport.assertTrue(message.contains(Thread.currentThread().getName()),
                "Report must name the borrowing thread, got: " + message);
        TestSupport.assertTrue(message.contains("PooledDataSourceTest"),
                "Report must name the borrowing line, got: " + message);
        dataSource.shutdown();
    }

    public void leakDetection_warnsWithBorrowingStack() throws Exception {
        // Arrange — a short threshold and the fastest eviction sweep allowed
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:leak-warn", "u", "p", 1);
        dataSource.setEvictionIntervalMs(1000);
        dataSource.setLeakDetectionThresholdMs(1);

        final List<LogRecord> records = Collections.synchronizedList(new ArrayList<LogRecord>());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.WARNING);
        POOL_LOGGER.addHandler(handler);

        Connection held = dataSource.getConnection();
        try {
            // Act — wait for the eviction thread to run its leak check
            String warning = null;
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline && warning == null) {
                Thread.sleep(50);
                warning = findLeakWarning(records);
            }

            // Assert — one warning naming the thread and the responsible line
            TestSupport.assertTrue(warning != null,
                    "Expected a leak warning within one eviction sweep, got none");
            TestSupport.assertTrue(warning.contains(Thread.currentThread().getName()),
                    "Warning must name the borrowing thread, got: " + warning);
            TestSupport.assertTrue(warning.contains("PooledDataSourceTest"),
                    "Warning must name the borrowing line, got: " + warning);
        } finally {
            POOL_LOGGER.removeHandler(handler);
            held.close();
            dataSource.shutdown();
        }
    }

    private static String findLeakWarning(List<LogRecord> records) {
        SimpleFormatter formatter = new SimpleFormatter();
        synchronized (records) {
            for (LogRecord record : records) {
                String text = formatter.formatMessage(record);
                if (text.contains("Possible connection leak")) {
                    return text;
                }
            }
        }
        return null;
    }

    public void getConnectionAndReleaseConnection_updatePoolState() throws Exception {
        // Arrange
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:test-a", "u", "p", 2);

        // Act
        Connection connection = dataSource.getConnection();
        boolean released = dataSource.releaseConnection(connection);

        // Assert
        TestSupport.assertTrue(connection != null, "Expected pooled connection");
        TestSupport.assertTrue(released, "Expected releaseConnection to succeed");
        TestSupport.assertEquals(2, dataSource.getIdleCount(), "All connections should be idle after release");
        TestSupport.assertEquals(0, dataSource.getActiveCount(), "No connections should be active after release");
    }

    public void invalidConnection_detectedOnBorrow() throws Exception {
        // Arrange
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:test-b", "u", "p", 2);
        // Borrow both connections and make them invalid, then return them to idle pool
        Connection conn1 = dataSource.getConnection();
        Connection conn2 = dataSource.getConnection();
        MockJdbc.createdStates.get(0).valid = false;
        MockJdbc.createdStates.get(1).valid = false;
        dataSource.releaseConnection(conn1);
        dataSource.releaseConnection(conn2);

        // Act — borrow again: pool should detect invalid connections on borrow,
        // close them, and create a fresh replacement
        Connection fresh = dataSource.getConnection();

        // Assert
        TestSupport.assertTrue(fresh != null, "Expected new valid connection");
        // Should have created at least one replacement connection
        TestSupport.assertTrue(MockJdbc.createdStates.size() >= 3,
            "Expected at least one replacement connection (created=" + MockJdbc.createdStates.size() + ")");
        // The invalid idle connections should have been closed on borrow attempt
        TestSupport.assertTrue(MockJdbc.createdStates.get(0).closed, "Expected first invalid connection to be closed");
        TestSupport.assertTrue(MockJdbc.createdStates.get(1).closed, "Expected second invalid connection to be closed");
        dataSource.releaseConnection(fresh);
        dataSource.shutdown();
    }
}
