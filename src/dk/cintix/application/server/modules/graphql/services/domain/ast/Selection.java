package dk.cintix.application.server.modules.graphql.services.domain.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Selection {
    private final String name;
    private final Map<String, Value> arguments = new HashMap<>();
    private final List<Selection> subSelections = new ArrayList<>();

    public Selection(String name) { this.name = name; }

    public String getName() { return name; }
    public Map<String, Value> getArguments() { return arguments; }
    public List<Selection> getSubSelections() { return subSelections; }
}
