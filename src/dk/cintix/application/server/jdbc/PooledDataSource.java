package dk.cintix.application.server.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 *
 * @author migo
 */
public class PooledDataSource implements javax.sql.DataSource {

    private static String url;
    private static String user;
    private static String password;

    private static int INITIAL_POOL_SIZE;
    private static int timeout = 30000;
    private static int executorPoolSize = 5;
    private static int validSocketTimeOut = 25;

    private static ExecutorService executorService;
    private static List<Connection> connectionPool;
    private static final List<Connection> usedConnections = new ArrayList<>();

    public PooledDataSource(String url, String user, String password, int INITIAL_POOL_SIZE) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.INITIAL_POOL_SIZE = INITIAL_POOL_SIZE;
        this.connectionPool = new ArrayList<>(INITIAL_POOL_SIZE);
        executorService = Executors.newFixedThreadPool(executorPoolSize);
        try {

            for (int index = 0; index < INITIAL_POOL_SIZE; index++) {
                createConnection(url, user, password);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
    
    public static int getTimeout() {
        return timeout;
    }

    public static void setTimeout(int timeout) {
        PooledDataSource.timeout = timeout;
    }

    public static int getExecutorPoolSize() {
        return executorPoolSize;
    }

    public static void setExecutorPoolSize(int executorPoolSize) {
        PooledDataSource.executorPoolSize = executorPoolSize;
    }

    public static int getValidSocketTimeOut() {
        return validSocketTimeOut;
    }

    public static void setValidSocketTimeOut(int validSocketTimeOut) {
        PooledDataSource.validSocketTimeOut = validSocketTimeOut;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public List<Connection> getConnectionPool() {
        return connectionPool;
    }

    public List<Connection> getUsedConnections() {
        return usedConnections;
    }

    public int getINITIAL_POOL_SIZE() {
        return INITIAL_POOL_SIZE;
    }

    public boolean releaseConnection(Connection connection) {
        connectionPool.add(connection);
        return usedConnections.remove(connection);
    }

    private Connection createConnection(String url, String user, String password) throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
//        connection.setNetworkTimeout(executorService, timeout);
        connectionPool.add(connection);
        return connection;
    }

    private void validatePool() throws SQLException {
        List<Connection> deadConnections = new ArrayList<>();
        for (int index = 0; index < usedConnections.size(); index++) {
            Connection connection = usedConnections.get(index);
            if (connection.isClosed() || !connection.isValid(validSocketTimeOut)) {
                deadConnections.add(connection);
                connection.close();
                connection = null;
                createConnection(url, user, password);
            }
        }

        for (int index = 0; index < deadConnections.size(); index++) {
            usedConnections.remove(deadConnections.get(index));
        }

        deadConnections.clear();
        deadConnections = null;
    }

    public int getSize() {
        return connectionPool.size() + usedConnections.size();
    }

    @Override
    public Connection getConnection() throws SQLException {
        validatePool();
        
        if (connectionPool.size() == 0) {
            throw new SQLException("Could not get a new connetion from the connection pool!");
        }
        
        Connection connection = connectionPool.remove(connectionPool.size() - 1);
        usedConnections.add(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String string, String string1) throws SQLException {
        validatePool();
        Connection connection = connectionPool.remove(connectionPool.size() - 1);
        usedConnections.add(connection);
        return connection;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter writer) throws SQLException {
        DriverManager.setLogWriter(writer);
    }

    @Override
    public void setLoginTimeout(int i) throws SQLException {
        DriverManager.setLoginTimeout(i);
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
    public <T> T unwrap(Class<T> type) throws SQLException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        throw new UnsupportedOperationException("Not supported.");
    }
}
