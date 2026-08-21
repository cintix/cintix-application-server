package dk.cintix.application.server.modules.graphql.services.domain;

/**
 * Signals a GraphQL client error such as a malformed query, invalid
 * arguments, unknown fields, or a query that exceeds configured limits.
 *
 * <p>The exception message is safe to return to clients. Internal server
 * errors should use a different exception path so callers can separate
 * client errors (400) from server errors (500).</p>
 *
 * @author cix
 */
public class GraphQLException extends RuntimeException {

    public GraphQLException(String message) {
        super(message);
    }

    public GraphQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
