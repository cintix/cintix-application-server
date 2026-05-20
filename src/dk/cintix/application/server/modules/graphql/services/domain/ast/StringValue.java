package dk.cintix.application.server.modules.graphql.services.domain.ast;

public class StringValue extends Value {
    private final String text;
    public StringValue(String text) { this.text = text; }
    public String getText() { return text; }
    @Override
    public String asString() { return text; }
}
