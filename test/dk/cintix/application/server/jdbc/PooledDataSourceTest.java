package dk.cintix.application.server.jdbc;

import dk.cintix.application.server.TestSupport;
import java.sql.Connection;

public class PooledDataSourceTest {

    public void runAll() throws Exception {
        getConnectionAndReleaseConnection_updatePoolState();
        validatePool_replacesInvalidUsedConnection();
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
        TestSupport.assertEquals(2, dataSource.getConnectionPool().size(), "Connection pool should return to initial size");
    }

    public void validatePool_replacesInvalidUsedConnection() throws Exception {
        // Arrange
        MockJdbc.registerDriver();
        MockJdbc.reset();
        PooledDataSource dataSource = new PooledDataSource("jdbc:mock:test-b", "u", "p", 1);
        Connection connection = dataSource.getConnection();
        MockJdbc.createdStates.get(0).valid = false;

        // Act
        try {
            dataSource.getConnection();
        } catch (Exception ignored) {
            // the method can fail if pool is exhausted during replacement timing
        }

        // Assert
        TestSupport.assertTrue(MockJdbc.createdStates.size() >= 2, "Expected invalid connection to trigger replacement");
        TestSupport.assertTrue(MockJdbc.createdStates.get(0).closed, "Expected invalid connection to be closed");
        dataSource.releaseConnection(connection);
    }
}
