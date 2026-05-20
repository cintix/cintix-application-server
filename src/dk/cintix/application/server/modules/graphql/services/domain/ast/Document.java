package dk.cintix.application.server.modules.graphql.services.domain.ast;

public class Document {
    private Operation operation;
    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }
}