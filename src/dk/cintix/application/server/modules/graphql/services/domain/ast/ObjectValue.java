package dk.cintix.application.server.modules.graphql.services.domain.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectValue extends Value {
    private final Map<String, Value> fields = new HashMap<>();
    public Map<String, Value> getFields() { return fields; }
    @Override
    public String asString() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Value> e : fields.entrySet()) {
            parts.add(e.getKey() + ": " + e.getValue().asString());
        }
        return "{ " + String.join(", ", parts) + " }";
    }
}
