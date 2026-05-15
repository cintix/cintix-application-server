package dk.cintix.application.server.modules.http.server.endpoint.events;

import dk.cintix.application.server.modules.http.server.services.domain.models.RestClient;

/**
 *
 * @author cix
 */
public interface HttpConnectionEvents {
    public void connected(RestClient client);
    public void disconnected(RestClient client);
}
