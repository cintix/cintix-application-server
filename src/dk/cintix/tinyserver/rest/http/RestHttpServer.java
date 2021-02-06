/*
 */
package dk.cintix.tinyserver.rest.http;

import dk.cintix.tinyserver.events.HttpRequestEvents;
import dk.cintix.tinyserver.events.HttpNotificationEvents;
import dk.cintix.tinyserver.events.HttpConnectionEvents;
import dk.cintix.tinyserver.rest.RestClient;
import dk.cintix.tinyserver.rest.RestEndPoint;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author cix
 */
public abstract class RestHttpServer {

    private static final Map<String, RestEndPoint> pathMapping = new LinkedHashMap<>();
    private final Map<String, RestClient> clientSessions = new LinkedHashMap<>();

    private HttpConnectionEvents connectionEvents;
    private HttpRequestEvents requestEvents;
    private HttpNotificationEvents notificationEvents;
    private InetSocketAddress address;
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void bind(InetSocketAddress address) throws Exception {
        bind(address, 50);
    }

    public void bind(InetSocketAddress address, int backlog) throws Exception {
        this.address = address;
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocket = serverSocketChannel.socket();
        serverSocket.bind(address, backlog);
    }

    public void addEndpoint(String path, RestEndPoint endpoint) {
        pathMapping.put(path, endpoint);
    }

    public void connectedEvent(RestClient client) {
        if (connectionEvents != null) {
            connectionEvents.connected(client);
        }
    }

    public void disconnectedEvent(RestClient client) {
        if (connectionEvents != null) {
            connectionEvents.disconnected(client);
        }
    }

    public void requestEvent(RestClient client, RestHttpRequest request) {
        if (requestEvents != null) {
            requestEvents.request(client, request);
        }
    }

    public void notifyEvent(String msg) {
        if (notificationEvents != null) {
            notificationEvents.notification(msg);
        }
    }

    public void setConnectionEvents(HttpConnectionEvents connectionEvents) {
        this.connectionEvents = connectionEvents;
    }

    public void setRequestEvents(HttpRequestEvents requestEvents) {
        this.requestEvents = requestEvents;
    }

    public void setNotificationEvents(HttpNotificationEvents notificationEvents) {
        this.notificationEvents = notificationEvents;
    }

    public boolean startServer() throws Exception {
        serverSocketChannel.configureBlocking(false);
        int validOps = serverSocketChannel.validOps();
        serverSocketChannel.register(selector, validOps, null);
        notifyEvent("Server start on " + address.toString());
        notifyEvent("Listering...");

        while (running) {
            int amount = selector.select(3000);
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            if (amount > 0)
            try {
                SelectionKey key = null;
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(serverSocketChannel, key);
                    }

                    if (key.isReadable()) {
                        handleRead(key);
                    }

                }
            } catch (Exception exception) {
            } finally {
                selectedKeys.clear();
            }
        }
        return running;
    }

    private void handleDisconnect(ServerSocketChannel mySocket, SelectionKey key) throws Exception {
        String sessionId = (key.attachment() != null) ? key.attachment().toString() : null;
        if (sessionId != null && clientSessions.containsKey(sessionId)) {
            RestClient restClient = clientSessions.get(sessionId);
            clientSessions.remove(sessionId);
            disconnectedEvent(restClient);
        }
        key.cancel();
    }

    private void handleAccept(ServerSocketChannel mySocket, SelectionKey key) throws Exception {
        SocketChannel client = mySocket.accept();
        RestClient restClient = new RestClient(client);
        key.attach(restClient.getSessionId());
        
        clientSessions.put(restClient.getSessionId(), restClient);
        
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, restClient.getSessionId());
        connectedEvent(restClient);
    }

    private void handleRead(SelectionKey key) throws Exception {
        String sessionId = (key.attachment() != null) ? key.attachment().toString() : null;
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        int read = client.read(buffer);
        if (read == -1) {
            handleDisconnect(serverSocketChannel, key);
        }
        String data = new String(buffer.array()).trim();
        if (data.length() > 0) {
            notifyEvent(data);
        }
    }
}
