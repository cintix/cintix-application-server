package dk.cintix.application.server.jdbc;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.database.services.PooledDataSource;
import java.sql.Connection;

public class PooledDataSourceTest {

    public void runAll() throws Exception {
        getConnectionAndReleaseConnection_updatePoolState();
        invalidConnection_detectedOnBorrow();
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
