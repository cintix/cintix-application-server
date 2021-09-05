/*
 */
package dk.cintix.tinyserver.rest.http;

import dk.cintix.tinyserver.Application;
import dk.cintix.tinyserver.rest.http.utils.HttpUtil;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.events.HttpRequestEvents;
import dk.cintix.tinyserver.events.HttpNotificationEvents;
import dk.cintix.tinyserver.events.HttpConnectionEvents;
import dk.cintix.tinyserver.io.ReflectionUtil;
import dk.cintix.tinyserver.rest.RestAction;
import dk.cintix.tinyserver.rest.RestClient;
import dk.cintix.tinyserver.rest.RestEndpoint;
import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.annotations.POST;
import dk.cintix.tinyserver.rest.annotations.PUT;
import dk.cintix.tinyserver.rest.annotations.DELETE;
import dk.cintix.tinyserver.rest.http.session.InternalClientSession;
import dk.cintix.tinyserver.rest.jsd.JsonServiceDescriptionEngine;
import dk.cintix.tinyserver.rest.response.Response;
import dk.cintix.tinyserver.web.MimeTypes;
import dk.cintix.tinyserver.web.engine.Document;
import dk.cintix.tinyserver.web.engine.DocumentEngine;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author cix
 */
public abstract class RestHttpServer {

    private static final Map<String, Map<String, RestEndpoint>> pathMapping = new LinkedHashMap<>();
    private final Map<String, RestClient> clientSessions = new LinkedHashMap<>();
    private final Map<String, String> documentationEndpoint = new LinkedHashMap<>();

    private HttpConnectionEvents connectionEvents;
    private HttpRequestEvents requestEvents;
    private HttpNotificationEvents notificationEvents;
    private InetSocketAddress address;
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final ByteBuffer dataBuffer = ByteBuffer.allocate(2048);
    private String documentRoot = "web";

    static {
        Application.set("DOCUMENT_ROOT", null);
    }

    public String getDocumentRoot() {
        if (!documentRoot.trim().endsWith("/")) {
            documentRoot = documentRoot.trim() + "/";
        }
        return documentRoot;
    }

    public void setDocumentRoot(String documentRoot) {
        this.documentRoot = documentRoot;
        Application.set("DOCUMENT_ROOT", getDocumentRoot());
    }

    public RestHttpServer() {
        if (!pathMapping.containsKey("get")) {
            pathMapping.put("get", new LinkedHashMap<>());
        }
        if (!pathMapping.containsKey("put")) {
            pathMapping.put("put", new LinkedHashMap<>());
        }
        if (!pathMapping.containsKey("post")) {
            pathMapping.put("post", new LinkedHashMap<>());
        }
        if (!pathMapping.containsKey("delete")) {
            pathMapping.put("delete", new LinkedHashMap<>());
        }
    }

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
        documentationEndpoint.put(path + "?jsd", JsonServiceDescriptionEngine.generateServiceDefination(path, null, endpoint));
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
            noop();
        }
        return running;
    }

    private void noop() {
        try {
            TimeUnit.NANOSECONDS.sleep(50);
        } catch (InterruptedException ex) {
        }
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
        if (client == null) {
            return;
        }
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
        try (SocketChannel client = (SocketChannel) key.channel()) {
            Response response = clientSession.getResponse();
            byte[] buildedResponse = response.build();

            ByteBuffer buffer = ByteBuffer.wrap(buildedResponse);
            client.write(buffer);
            InternalClientSession newSession = new InternalClientSession(clientSession.getSessionId());
        }
        handleDisconnect(key);
    }

    private void handleRead(SelectionKey key) throws Exception {
        InternalClientSession clientSession = readAttachment(key);
        SocketChannel client = (SocketChannel) key.channel();
        RestClient restClient = clientSessions.get(clientSession.getSessionId());
        dataBuffer.clear();
        String data = "";

        int read;
        int totalRead = 0;
        int MAX_BYTES = 1024 * 1024 * 5; // 5MB       

        if (client.socket() == null && client.socket().getInputStream() == null && client.socket().getInputStream().available() < 1) {
            return;
        }

        while ((read = client.read(dataBuffer)) > 0) {
            totalRead += read;
            if (totalRead > MAX_BYTES) {
                break;
            }

            dataBuffer.flip();
            byte[] bytes = new byte[dataBuffer.limit()];
            dataBuffer.get(bytes);
            data += new String(bytes);
            dataBuffer.clear();
        }

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

        int indexOfFormdata = headerData.indexOf("\r\n\r\n");
        String rawPost = headerData.substring(indexOfFormdata + 4);

        if (indexOfFormdata == -1) {
            indexOfFormdata = headerData.indexOf("\n\n");
            if (indexOfFormdata != -1) {
                rawPost = headerData.substring(indexOfFormdata + 4);
            }
        }

        method = methodAndPath[0].toUpperCase();
        for (int index = 1; index < methodAndPath.length - 1; index++) {
            contextPath += methodAndPath[index] + " ";
        }

        contextPath = contextPath.trim();

        if (!documentationEndpoint.containsKey(contextPath)) {
            contextPath = HttpUtil.parseQueryStrings(contextPath, queryStrings);
            contextPath = contextPath.trim();
        }

        if (contextPath.endsWith("/")) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }

        linesProcessed = HttpUtil.parseHeaderKeys(requestLines, headers, linesProcessed);
        HttpUtil.parsePostFields(linesProcessed, requestLines, postFields);
        RestHttpRequest httpRequest = new RestHttpRequest(headers, queryStrings, postFields, inputStream, method, contextPath, rawPost);
        requestEvent(restClient, httpRequest);
        return httpRequest;
    }

    private boolean isRequestADocument(String context) {
        File jailedRoot = new File(documentRoot);
        File checkFile = new File(getDocumentRoot() + context);
        if (checkFile.exists()) {
            if (checkFile.getAbsolutePath().startsWith(jailedRoot.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private Response handleRequestMapping(Map<String, Map<String, RestEndpoint>> pathMapping, RestHttpRequest request) throws Exception {
        String contextPath = request.getContextPath();
        if (contextPath.equals("")) {
            contextPath = "index.htm";
        }

        if (isRequestADocument(contextPath) && Application.get("DOCUMENT_ROOT") != null) {
            File documentFile = new File(getDocumentRoot() + contextPath);
            if (contextPath.toLowerCase().endsWith(".htm") || contextPath.toLowerCase().endsWith(".html")) {
                Document document = DocumentEngine.readTemplate(request, documentFile);
                String contentData = document.getData();
                return new Response().OK().ContentType("text/html").data(contentData);
            }

            String fileExt = contextPath.substring(contextPath.lastIndexOf(".") + 1);
            String contextType = MimeTypes.ContentType(fileExt);

            byte[] fileContent = Files.readAllBytes(documentFile.toPath());
            return new Response().OK().ContentType(contextType).Content(fileContent);
        }

        if (documentationEndpoint.containsKey(contextPath)) {
            return new Response().OK().ContentType("application/json").data(documentationEndpoint.get(contextPath));
        }

        Map<String, RestEndpoint> requestMap = pathMapping.get(request.getMethod().toLowerCase());
        RestAction restAction = locateEndpoint(requestMap, contextPath.trim());

        if (restAction != null) {
            return restAction.process(request);
        } else {
            return new Response().NotFound();
        }
    }

    private RestAction locateEndpoint(Map<String, RestEndpoint> mapping, String contextPath) throws Exception {
        if (mapping.containsKey(contextPath)) {
            return new RestAction(mapping.get(contextPath), new LinkedList<>());
        }

        List<String> regexMApping = new LinkedList<>();
        regexMApping.addAll(mapping.keySet());

        Collections.sort(regexMApping, Comparator.comparing(String::length));
        Collections.reverse(regexMApping);

        for (String pattern : regexMApping) {

            if (!pattern.startsWith("^")) {
                continue;
            }

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

    private void registerEndpoint(Map<String, Map<String, RestEndpoint>> pathMapping, String path, Object endpoint) {
        String base = path;
        Method[] methods = endpoint.getClass().getDeclaredMethods();

        for (Method method : methods) {
            Method readFrom = ReflectionUtil.getBestDescribedMethod(method, endpoint);
            String httpMethod = "get";

            if (readFrom.isAnnotationPresent(POST.class)) {
                httpMethod = "post";
            }
            if (readFrom.isAnnotationPresent(PUT.class)) {
                httpMethod = "put";
            }
            if (readFrom.isAnnotationPresent(DELETE.class)) {
                httpMethod = "delete";
            }

            Map<String, RestEndpoint> httpMethodMap = pathMapping.get(httpMethod);

            if (readFrom.isAnnotationPresent(Action.class)) {
                Action action = readFrom.getAnnotation(Action.class);
                String actionPath = action.path();

                if (!actionPath.startsWith("/")) {
                    actionPath = "/" + action.path();
                }

                if (action.path().equals("/")) {
                    actionPath = "";
                }

                String urlPattern = HttpUtil.complieRegexFromPath(base + actionPath);
                httpMethodMap.put(urlPattern, new RestEndpoint(base + actionPath, method, endpoint));
                httpMethodMap.put(base + actionPath, new RestEndpoint(base + actionPath, method, endpoint));
                pathMapping.put(httpMethod, httpMethodMap);

            }
        }

    }

}
