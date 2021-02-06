/*
 */
package dk.cintix.tinyserver.events;

import dk.cintix.tinyserver.rest.RestClient;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;

/**
 *
 * @author cix
 */
public interface HttpRequestEvents {
    public void request(RestClient client, RestHttpRequest request);
}
