package dk.cintix.application.server.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author migo
 */
public class TransactionableConnection implements AutoCloseable {

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
        connection.createStatement().execute("ROLLBACK;");
        close();
    }

    public void beginTransaction() throws SQLException {
        transactionType = TransactionType.TRANSACTION;
        connection.createStatement().execute("begin transaction;");
    }

    public void commit() throws SQLException {
        transactionType = TransactionType.AUTOCOMMIT;
        try {
            connection.createStatement().execute("COMMIT;");
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }
    }

    public void generateSavepoint() throws SQLException {
        savepointer++;
        try {
            connection.createStatement().execute("SAVEPOINT SAVE_POINT_" + savepointer + ";");
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }

    }
    
    
    public void rollbackToLastSavepoint() throws SQLException {
        try {
            connection.createStatement().execute("ROLLBACK TO SAVE_POINT_" + savepointer + ";");
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }        
    }

    public void generateCustomSavepoint(String name) throws SQLException {
        try {
            connection.createStatement().execute("SAVEPOINT SAVE_POINT_" + name + ";");
        } catch (SQLException sQLException) {
            inErrorState = true;
            throw sQLException;
        }

    }
    
    
    public void rollbackToCustomSavepoint(String name) throws SQLException {
        try {
            connection.createStatement().execute("ROLLBACK TO SAVE_POINT_" + name + ";");
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
        }
        super.finalize();
    }

}
