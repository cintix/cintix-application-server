package dk.cintix.application.server.modules.graphql.services.domain.ast;

public class NullValue extends Value {
    @Override
    public String asString() { return "null"; }
}
