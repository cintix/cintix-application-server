/*
 */
package dk.cintix.tinyserver.demo;

import dk.cintix.tinyserver.demo.endpoint.HelloWorldRestEndPoint;
import dk.cintix.tinyserver.rest.http.RestHttpServer;
import dk.cintix.tinyserver.rest.RestClient;
import dk.cintix.tinyserver.events.HttpNotificationEvents;
import dk.cintix.tinyserver.events.HttpConnectionEvents;
import dk.cintix.tinyserver.io.Log;
import java.net.InetSocketAddress;

/**
 *
 * @author migo
 */
public class Server extends RestHttpServer implements HttpNotificationEvents, HttpConnectionEvents {
    
    private final Log LOG = Log.instance();
    
    public Server() {
        try {
           
            setNotificationEvents(this);
            setConnectionEvents(this);
            
            bind(new InetSocketAddress("0.0.0.0", 8080));
            addEndpoint("/api/hello", new HelloWorldRestEndPoint());
            
            startServer();
            
        } catch (Exception exception) {
            exception.printStackTrace();
            LOG.error(exception.toString());
        }
    }
    
    public static void main(String[] args) {
        Server server = new Server();
    }
    
    @Override
    public void notification(String msg) {
        LOG.log(msg);
    }

    @Override
    public void connected(RestClient client) {
        System.out.println("User " + client + " connected");
    }

    @Override
    public void disconnected(RestClient client) {
        System.out.println("User " + client + " disconnected");
    }
    
}