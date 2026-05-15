package dk.cintix.application.server.modules.http.server.services.graphql.ast;

public class BooleanValue extends Value {
    private final boolean value;
    public BooleanValue(boolean value) { this.value = value; }
    public boolean getValue() { return value; }
    @Override
    public String asString() { return Boolean.toString(value).toLowerCase(); }
}

