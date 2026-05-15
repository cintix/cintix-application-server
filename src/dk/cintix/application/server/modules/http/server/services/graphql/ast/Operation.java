package dk.cintix.application.server.modules.http.server.services.graphql.ast;

import java.util.ArrayList;
import java.util.List;

public class Operation {
    private OperationType type = OperationType.QUERY;
    private final List<Selection> selections = new ArrayList<>();

    public OperationType getType() { return type; }
    public void setType(OperationType type) { this.type = type; }
    public List<Selection> getSelections() { return selections; }
}
