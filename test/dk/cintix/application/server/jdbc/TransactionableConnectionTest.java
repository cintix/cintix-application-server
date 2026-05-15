package dk.cintix.application.server.jdbc;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.database.services.TransactionableConnection;

public class TransactionableConnectionTest {

    public void runAll() throws Exception {
        beginCommit_executesStatementsAndClosesStatements();
        rollback_executesStatementAndClosesConnection();
    }

    public void beginCommit_executesStatementsAndClosesStatements() throws Exception {
        // Arrange
        MockJdbc.reset();
        MockJdbc.MockConnectionState state = new MockJdbc.MockConnectionState();
        TransactionableConnection tx = new TransactionableConnection(MockJdbc.newConnection(state));

        // Act
        tx.beginTransaction();
        tx.commit();
        tx.close();

        // Assert
        TestSupport.assertTrue(MockJdbc.executedSql.contains("begin transaction;"), "Missing begin transaction SQL");
        TestSupport.assertTrue(MockJdbc.executedSql.contains("COMMIT;"), "Missing commit SQL");
        TestSupport.assertTrue(state.statementClosedCount >= 2, "Statements should be closed via try-with-resources");
        TestSupport.assertTrue(state.closed, "Connection should be closed after commit and close");
    }

    public void rollback_executesStatementAndClosesConnection() throws Exception {
        // Arrange
        MockJdbc.reset();
        MockJdbc.MockConnectionState state = new MockJdbc.MockConnectionState();
        TransactionableConnection tx = new TransactionableConnection(MockJdbc.newConnection(state));

        // Act
        tx.beginTransaction();
        tx.rollback();

        // Assert
        TestSupport.assertTrue(MockJdbc.executedSql.contains("ROLLBACK;"), "Missing rollback SQL");
        TestSupport.assertTrue(state.closed, "Connection should close after rollback");
    }
}
