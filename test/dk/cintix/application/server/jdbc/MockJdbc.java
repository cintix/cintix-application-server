package dk.cintix.application.server.jdbc;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public final class MockJdbc {

    public static final List<String> executedSql = new ArrayList<>();
    public static final List<MockConnectionState> createdStates = new ArrayList<>();

    private MockJdbc() {
    }

    public static void reset() {
        synchronized (MockJdbc.class) {
            executedSql.clear();
            createdStates.clear();
        }
    }

    public static void registerDriver() throws SQLException {
        DriverManager.registerDriver(new MockDriver());
    }

    public static Connection newConnection(MockConnectionState state) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("isClosed".equals(name)) {
                        return state.closed;
                    }
                    if ("close".equals(name)) {
                        state.closed = true;
                        return null;
                    }
                    if ("isValid".equals(name)) {
                        return state.valid;
                    }
                    if ("createStatement".equals(name)) {
                        return newStatement(state);
                    }
                    if ("toString".equals(name)) {
                        return "MockConnection";
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }

    private static Statement newStatement(MockConnectionState state) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("execute".equals(name) && args != null && args.length == 1) {
                        executedSql.add(String.valueOf(args[0]));
                        return true;
                    }
                    if ("close".equals(name)) {
                        state.statementClosedCount++;
                        return null;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }

    public static final class MockConnectionState {
        public boolean closed;
        public boolean valid = true;
        public int statementClosedCount;
    }

    public static final class MockDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            MockConnectionState state = new MockConnectionState();
            synchronized (MockJdbc.class) {
                createdStates.add(state);
            }
            return newConnection(state);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:mock:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
