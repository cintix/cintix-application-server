/*
 */
package dk.cintix.tinyserver.demo;

import dk.cintix.tinyserver.demo.endpoint.HelloWorldRestEndPoint;
import dk.cintix.tinyserver.demo.entities.Channel;
import dk.cintix.tinyserver.rest.http.RestHttpServer;
import dk.cintix.tinyserver.io.Log;
import dk.cintix.tinyserver.jdbc.DataSourceManager;
import dk.cintix.tinyserver.jdbc.EntityManager;
import dk.cintix.tinyserver.jdbc.PooledDataSource;
import java.net.InetSocketAddress;
import java.util.List;

/**
 *
 * @author migo
 */
public class Server extends RestHttpServer {

    private final Log LOG = Log.instance();

    private void start() {
        try {

            PooledDataSource pooledDataSource = new PooledDataSource("jdbc:postgresql://localhost:5432/epgcore", "epgcore", "epgcore", 20);
            DataSourceManager.addDataSource("jdbc/epg", pooledDataSource);

            List<Channel> channels = EntityManager.create(Channel.class).loadAll();
            for (Channel channel : channels) {
                System.out.println(channel.toString());
            }

            bind(new InetSocketAddress("0.0.0.0", 9090));
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
