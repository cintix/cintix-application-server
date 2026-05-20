package dk.cintix.application.server.modules.graphql.services.domain.ast;


public class NumberValue extends Value {
    private final String text;
    public NumberValue(String text) { this.text = text; }
    public String getText() { return text; }
    public int asInt() { return Integer.parseInt(text); }
    @Override
    public String asString() { return text; }
}

