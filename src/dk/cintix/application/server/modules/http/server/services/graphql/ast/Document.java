package dk.cintix.application.server.modules.http.server.services.graphql.ast;

public class Document {
    private Operation operation;
    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }
}