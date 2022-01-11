/*
 */
package dk.cintix.application.server.events;

import dk.cintix.application.server.rest.RestClient;
import dk.cintix.application.server.rest.http.request.RestHttpRequest;

/**
 *
 * @author cix
 */
public interface HttpRequestEvents {
    public void request(RestClient client, RestHttpRequest request);
}
