package dk.cintix.application.server.modules.http.server.services.graphql.ast;

public class NullValue extends Value {
    @Override
    public String asString() { return "null"; }
}
