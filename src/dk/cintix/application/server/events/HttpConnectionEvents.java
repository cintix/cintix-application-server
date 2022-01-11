/*
 */
package dk.cintix.application.server.events;

import dk.cintix.application.server.rest.RestClient;

/**
 *
 * @author cix
 */
public interface HttpConnectionEvents {
    public void connected(RestClient client);
    public void disconnected(RestClient client);    
}
