package dk.cintix.application.server.modules.http.server.endpoint.events;

import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestClient;

/**
 *
 * @author cix
 */
public interface HttpRequestEvents {
    public void request(RestClient client, RestHttpRequest request);
}
