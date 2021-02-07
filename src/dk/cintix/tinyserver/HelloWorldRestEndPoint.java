/*
 */
package dk.cintix.tinyserver;

import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.annotations.Inject;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.rest.response.Response;

/**
 *
 * @author cix
 */
public class HelloWorldRestEndPoint {

    @Inject
    RestHttpRequest request;

    @Action(path = "/{name}")
    public Response sayHelloBack(String name) {
        return new Response().OK();
    }

    @Action(path = "/world")
    public Response sayHelloToTheWorld() {
        return new Response().OK();
    }

}
