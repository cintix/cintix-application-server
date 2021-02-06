/*
 */
package dk.cintix.tinyserver.events;

import dk.cintix.tinyserver.rest.RestClient;

/**
 *
 * @author cix
 */
public interface HttpConnectionEvents {
    public void connected(RestClient client);
    public void disconnected(RestClient client);    
}
