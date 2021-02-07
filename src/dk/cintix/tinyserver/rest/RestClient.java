/*
 */
package dk.cintix.tinyserver.rest;

import java.nio.channels.SocketChannel;

/**
 *
 * @author cix
 */
public class RestClient {

    private String localAddress;
    private String remoteAddress;
    private String sessionId;
    private long created;
    
    public RestClient(SocketChannel sc) throws Exception {
        localAddress = sc.getLocalAddress().toString();
        remoteAddress = sc.getRemoteAddress().toString();
        created = System.currentTimeMillis();
        sessionId = "SS-" + created;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreated() {
        return created;
    }

    @Override
    public String toString() {
        return "RestClient{" + "localAddress=" + localAddress + ", remoteAddress=" + remoteAddress + ", sessionId=" + sessionId + ", created=" + created + '}';
    }

}
