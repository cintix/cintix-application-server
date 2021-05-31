/*
 */
package dk.cintix.tinyserver.demo;

import dk.cintix.tinyserver.demo.endpoint.HelloWorldRestEndPoint;
import dk.cintix.tinyserver.rest.http.RestHttpServer;
import dk.cintix.tinyserver.io.Log;
import java.net.InetSocketAddress;

/**
 *
 * @author migo
 */
public class Server extends RestHttpServer {
    private final Log LOG = Log.instance();

    private void start() {
        try {

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
        server.start();
    }

}
