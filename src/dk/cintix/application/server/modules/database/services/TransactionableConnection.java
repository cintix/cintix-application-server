package dk.cintix.application.server.modules.database.services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author migo
 */
public class TransactionableConnection implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(TransactionableConnection.class.getName());
    private boolean inErrorState;
    private int savepointer = 0;

    public boolean isClosed() throws SQLException {
        return connection.isClosed();
    }

    public enum TransactionType {
        TRANSACTION, AUTOCOMMIT
    };

    private final java.sql.Connection connection;

    public TransactionableConnection(java.sql.Connection connection) {
        this.connection = connection;
    }

    private TransactionType transactionType = TransactionType.AUTOCOMMIT;

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (transactionType == TransactionType.TRANSACTION) {
            if (inErrorState) {
                rollback();
                inErrorState = false;
            }
            return;
        }

        if (inErrorState) {
            rollback();
            inErrorState = false;
        }

        connection.close();
    }

    public void rollback() throws SQLException {
        if (transactionType == TransactionType.AUTOCOMMIT) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK;");
        }
        transactionType = TransactionType.AUTOCOMMIT;
        connection.close();
    }

    public void beginTransaction() throws SQLException {
        transactionType = TransactionType.TRANSACTION;
        try (Statement statement = connection.createStatement()) {
            statement.execute("begin transaction;");
        }
    }

    public void commit() throws SQLException {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("COMMIT;");
            }
            transactionType = TransactionType.AUTOCOMMIT;
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }
    }

    public void generateSavepoint() throws SQLException {
        savepointer++;
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SAVEPOINT SAVE_POINT_" + savepointer + ";");
            }
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }

    }


    public void rollbackToLastSavepoint() throws SQLException {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ROLLBACK TO SAVE_POINT_" + savepointer + ";");
            }
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }
    }

    public void generateCustomSavepoint(String name) throws SQLException {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SAVEPOINT SAVE_POINT_" + name + ";");
            }
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }

    }


    public void rollbackToCustomSavepoint(String name) throws SQLException {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ROLLBACK TO SAVE_POINT_" + name + ";");
            }
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }
    }



    @Override
    protected void finalize() throws Throwable {
        try {
            if (connection != null && !connection.isClosed()) {
                commit();
                connection.close();
            }
        } catch (SQLException sQLException) {
            logger.log(Level.WARNING, "Failed to close connection during finalize", sQLException);
        }
        super.finalize();
    }

}
