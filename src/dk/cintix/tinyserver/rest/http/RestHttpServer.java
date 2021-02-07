/*
 */
package dk.cintix.tinyserver.rest.http;

import dk.cintix.tinyserver.rest.http.utils.HttpUtil;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.events.HttpRequestEvents;
import dk.cintix.tinyserver.events.HttpNotificationEvents;
import dk.cintix.tinyserver.events.HttpConnectionEvents;
import dk.cintix.tinyserver.rest.RestAction;
import dk.cintix.tinyserver.rest.RestClient;
import dk.cintix.tinyserver.rest.RestEndpoint;
import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.http.session.InternalClientSession;
import dk.cintix.tinyserver.rest.response.Response;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author cix
 */
public abstract class RestHttpServer {

    private static final Map<String, RestEndpoint> pathMapping = new LinkedHashMap<>();
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

    public void addEndpoint(String path, Object endpoint) {
        registerEndpoint(pathMapping, path, endpoint);
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

                    if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (Exception exception) {
            } finally {
                selectedKeys.clear();
            }
        }
        return running;
    }

    private void handleDisconnect(SelectionKey key) throws Exception {
        InternalClientSession clientSession = readAttachment(key);
        key.cancel();

        RestClient restClient = clientSessions.get(clientSession.getSessionId());
        clientSessions.remove(clientSession.getSessionId());
        disconnectedEvent(restClient);
    }

    private void handleAccept(ServerSocketChannel mySocket, SelectionKey key) throws Exception {
        SocketChannel client = mySocket.accept();
        RestClient restClient = new RestClient(client);
        key.attach(restClient.getSessionId());

        clientSessions.put(restClient.getSessionId(), restClient);
        InternalClientSession clientSession = new InternalClientSession(restClient.getSessionId());

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, clientSession);
        connectedEvent(restClient);
    }

    private void handleWrite(SelectionKey key) throws Exception {
        InternalClientSession clientSession = readAttachment(key);
        SocketChannel client = (SocketChannel) key.channel();
        Response response = clientSession.getResponse();
        byte[] buildedResponse = response.build();

        System.out.println(new String(buildedResponse));

        ByteBuffer buffer = ByteBuffer.wrap(buildedResponse);
        client.write(buffer);
        InternalClientSession newSession = new InternalClientSession(clientSession.getSessionId());
        client.close();
        handleDisconnect(key);
    }

    private void handleRead(SelectionKey key) throws Exception {
        System.out.println("clientSessions " + clientSessions.size());

        InternalClientSession clientSession = readAttachment(key);
        SocketChannel client = (SocketChannel) key.channel();
        RestClient restClient = clientSessions.get(clientSession.getSessionId());

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int read = client.read(buffer);
        if (read == -1) {
            handleDisconnect(key);
        }

        String data = new String(buffer.array()).trim();

        if (data.length() > 0) {
            notifyEvent(data);
            RestHttpRequest request = parseRequest(restClient, client, data);
            Response response = handleRequestMapping(pathMapping, request);
            InternalClientSession session = new InternalClientSession(clientSession.getSessionId(), response);
            client.register(selector, SelectionKey.OP_WRITE, session);
        }

    }

    private InternalClientSession readAttachment(SelectionKey key) throws Exception {
        if (key.attachment() != null) {
            return (InternalClientSession) key.attachment();
        }
        throw new Exception("Unregistered client read (no session)");
    }

    private RestHttpRequest parseRequest(RestClient restClient, SocketChannel client, String headerData) throws Exception {
        final Map<String, String> headers = new LinkedHashMap<>();
        final Map<String, String> queryStrings = new LinkedHashMap<>();
        final Map<String, String> postFields = new LinkedHashMap<>();
        final InputStream inputStream = client.socket().getInputStream();

        String contextPath = "";
        String method = "GET";
        String[] requestLines = headerData.split("\n");
        String[] methodAndPath = requestLines[0].split(" ");
        int linesProcessed = 0;

        method = methodAndPath[0].toUpperCase();
        for (int index = 1; index < methodAndPath.length - 1; index++) {
            contextPath += methodAndPath[index] + " ";
        }

        contextPath.trim();
        contextPath = HttpUtil.parseQueryStrings(contextPath, queryStrings);

        linesProcessed = HttpUtil.parseHeaderKeys(requestLines, headers, linesProcessed);
        HttpUtil.parsePostFields(linesProcessed, requestLines, postFields);

        RestHttpRequest httpRequest = new RestHttpRequest(headers, queryStrings, postFields, inputStream, method, contextPath);
        requestEvent(restClient, httpRequest);

        return httpRequest;
    }

    private Response handleRequestMapping(Map<String, RestEndpoint> pathMapping, RestHttpRequest request) throws Exception {
        String contextPath = request.getContextPath();
        RestAction restAction = locateEndpint(pathMapping, contextPath.trim());
        if (restAction != null) {
            return restAction.process();
        } else {
            return new Response().NotFound();
        }
    }

    private RestAction locateEndpint(Map<String, RestEndpoint> mapping, String contextPath) throws Exception {

        List<String> regexMApping = new LinkedList<>();
        regexMApping.addAll(mapping.keySet());

        Collections.sort(regexMApping, Comparator.comparing(String::length));
        Collections.reverse(regexMApping);

        for (String pattern : regexMApping) {
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(contextPath);
            boolean found = false;
            List<String> arguments = new LinkedList<>();

            while (matcher.find()) {
                found = true;
                for (int index = 2; index < matcher.groupCount() + 1; index++) {
                    arguments.add(matcher.group(index));
                }
            }
            if (found) {
                return new RestAction(mapping.get(pattern), arguments);
            }
        }
        return null;
    }

    private void registerEndpoint(Map<String, RestEndpoint> pathMapping, String path, Object endpoint) {
        String base = path;
        Method[] methods = endpoint.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Action.class)) {
                Action action = method.getAnnotation(Action.class);
                String urlPattern = HttpUtil.complieRegexFromPath(base + action.path());
                pathMapping.put(urlPattern, new RestEndpoint(base + action.path(), method, endpoint));
            }
        }

    }

}
