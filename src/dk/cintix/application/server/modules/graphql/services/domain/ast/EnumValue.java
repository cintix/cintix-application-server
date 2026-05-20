package dk.cintix.application.server.modules.graphql.services.domain.ast;

public class EnumValue extends Value {
    private final String name;
    public EnumValue(String name) { this.name = name; }
    public String getName() { return name; }
    @Override
    public String asString() { return name; }
}
