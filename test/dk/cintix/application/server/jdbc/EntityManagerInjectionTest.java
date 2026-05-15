package dk.cintix.application.server.jdbc;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.database.annotations.Entity;
import dk.cintix.application.server.modules.database.annotations.InjectConnection;
import dk.cintix.application.server.modules.database.services.EntityManager;
import dk.cintix.application.server.modules.database.services.TransactionableConnection;
import java.lang.reflect.Proxy;
import java.sql.Connection;

public class EntityManagerInjectionTest {

    public void runAll() throws Exception {
        createWithTransactionableConnectionInjectsJdbcConnection();
    }

    public void createWithTransactionableConnectionInjectsJdbcConnection() throws Exception {
        // Arrange
        Connection jdbcConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
        TransactionableConnection tx = new TransactionableConnection(jdbcConnection);

        // Act
        TestManager manager = EntityManager.create(TestManager.class, tx);

        // Assert
        TestSupport.assertTrue(manager != null, "Manager should be created");
        TestSupport.assertTrue(manager.connection == jdbcConnection, "Injected value should be raw JDBC connection");
    }

    @Entity(manager = TestManager.class)
    public static class TestManager {
        @InjectConnection
        Connection connection;
    }
}
