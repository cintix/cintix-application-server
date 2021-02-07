/*
 */
package dk.cintix.tinyserver.rest;

import java.util.List;

/**
 *
 * @author cix
 */
public class RestAction {

    private final List<String> arguments;
    private final RestEndpoint endpoint;

    public RestAction(RestEndpoint endpoint, List<String> arguments) {
        this.arguments = arguments;
        this.endpoint = endpoint;
    }

    public void addArgument(String arg) {
        arguments.add(arg);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public RestEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return "RestAction{" + "arguments=" + arguments + ", endpoint=" + endpoint + '}';
    }

}
