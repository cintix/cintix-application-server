package dk.cintix.application.server.modules.http.server.endpoint;

import dk.cintix.application.server.infrastructure.Application;
import dk.cintix.application.server.infrastructure.ReflectionUtil;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.DELETE;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.infrastructure.annotations.PUT;
import dk.cintix.application.server.infrastructure.annotations.Timeout;
import dk.cintix.application.server.infrastructure.annotations.OnBinary;
import dk.cintix.application.server.infrastructure.annotations.OnClose;
import dk.cintix.application.server.infrastructure.annotations.OnError;
import dk.cintix.application.server.infrastructure.annotations.OnMessage;
import dk.cintix.application.server.infrastructure.annotations.OnOpen;
import dk.cintix.application.server.infrastructure.annotations.WebSocket;
import dk.cintix.application.server.modules.http.server.HttpModule;
import dk.cintix.application.server.modules.http.server.services.JsonServiceDescriptionEngine;
import dk.cintix.application.server.modules.http.server.services.WebSocketService;
import dk.cintix.application.server.modules.http.server.services.jsd.models.API;
import dk.cintix.application.server.modules.http.server.services.jsd.models.Service;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpConnectionEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpNotificationEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpRequestEvents;
import dk.cintix.application.server.modules.http.server.services.RestActionService;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestClient;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestEndpoint;
import dk.cintix.html.engine.HTMLEngine;
import dk.cintix.application.server.modules.mcp.McpDispatcher;
import dk.cintix.application.server.modules.mcp.McpEndpoint;
import dk.cintix.application.server.modules.mcp.McpRegistry;
import dk.cintix.application.server.modules.openapi.OpenApiEndpoint;
import dk.cintix.application.server.modules.openapi.OpenApiService;
import java.io.File;
import java.io.IOException;
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
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

/**
 *
 * @author cix
 */
public abstract class RestHttpServer implements HttpModule {

    private static final Logger logger = Logger.getLogger(RestHttpServer.class.getName());
    private final Map<String, Map<String, RestEndpoint>> pathMapping = new LinkedHashMap<>();
    private volatile Map<String, Map<String, RestEndpoint>> frozenPathMapping;
    private final Map<String, RestClient> clientSessions = new ConcurrentHashMap<>();
    private final Map<String, Service> documentationEndpoint = new ConcurrentHashMap<>();
    private final List<HttpModule.RequestFilter> requestFilters = new CopyOnWriteArrayList<>();
    private final WebSocketService webSocketService = new WebSocketService();

    private HttpConnectionEvents connectionEvents;
    private HttpRequestEvents requestEvents;
    private HttpNotificationEvents notificationEvents;
    private InetSocketAddress address;
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private volatile boolean shuttingDown;
    private volatile int drainTimeoutMs = 10_000;
    private final List<HealthCheck> healthChecks = new ArrayList<>();
    private volatile String healthPath = "/health";
    private final long startTime = System.currentTimeMillis();
    private volatile int defaultRequestTimeoutMs = 30_000;
    private volatile int idleReadTimeoutMs = 60_000;
    private final ByteBuffer dataBuffer = ByteBuffer.allocate(2048);
    private String documentRoot = "web";
    private ExecutorService workerPool;
    private final ConcurrentLinkedQueue<SelectionKey> completionQueue = new ConcurrentLinkedQueue<>();
    private int workerThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private int maxQueueSize = 1000;

    public int getPort() {
        if (serverSocket != null && serverSocket.isBound()) {
            return serverSocket.getLocalPort();
        }
        return -1;
    }

    public void setWorkerThreads(int threads) {
        this.workerThreads = Math.max(1, threads);
    }

    public void setMaxQueueSize(int size) {
        this.maxQueueSize = Math.max(1, size);
    }

    /**
     * Sets the maximum time (ms) to wait for in-flight requests to complete
     * during graceful shutdown. Default: 10 seconds.
     */
    public void setDrainTimeoutMs(int ms) {
        this.drainTimeoutMs = Math.max(100, ms);
    }

    /**
     * Registers a health check probe. Health checks run inline on the event loop
     * when {@code GET /health} is requested — they bypass the worker pool.
     */
    public void addHealthCheck(HealthCheck check) {
        healthChecks.add(check);
    }

    /**
     * Sets the path for the health endpoint (default: {@code /health}).
     */
    public void setHealthPath(String path) {
        this.healthPath = path;
    }

    /**
     * Sets the default request processing timeout (ms) for all endpoints.
     * Individual endpoints can override this with {@code @Timeout(ms = ...)}.
     * Use 0 to disable the timeout. Default: 30 seconds.
     */
    public void setDefaultRequestTimeoutMs(int ms) {
        this.defaultRequestTimeoutMs = Math.max(0, ms);
    }

    public int getDefaultRequestTimeoutMs() { return defaultRequestTimeoutMs; }

    /**
     * Sets the maximum time (ms) a connection may be idle while sending
     * a request before being closed. Default: 60 seconds.
     */
    public void setIdleReadTimeoutMs(int ms) {
        this.idleReadTimeoutMs = Math.max(1000, ms);
    }

    public int getIdleReadTimeoutMs() { return idleReadTimeoutMs; }

    /**
     * Returns a snapshot of all registered REST endpoints.
     * Returns the frozen mapping if the server has started, otherwise the live mapping.
     */
    public Map<String, Map<String, RestEndpoint>> getRegisteredEndpoints() {
        Map<String, Map<String, RestEndpoint>> source = frozenPathMapping != null ? frozenPathMapping : pathMapping;
        Map<String, Map<String, RestEndpoint>> unmodifiable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, RestEndpoint>> entry : source.entrySet()) {
            unmodifiable.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(unmodifiable);
    }

    /**
     * Executes all registered health checks and returns a JSON status response.
     * Runs inline on the event loop — must be fast.
     */
    private Response executeHealthCheck() {
        boolean allUp = true;
        long uptimeMs = System.currentTimeMillis() - startTime;
        StringBuilder json = new StringBuilder(256);
        json.append('{');

        // Write status first
        json.append("\"status\":\"");
        json.append("PLACEHOLDER");  // will replace later
        json.append('"');

        for (HealthCheck check : healthChecks) {
            json.append(',');
            String result = check.check();
            boolean up = "UP".equals(result);
            if (!up) {
                allUp = false;
            }
            json.append('"');
            json.append(escapeJson(check.name()));
            json.append("\":\"");
            json.append(up ? "UP" : escapeJson(result));
            json.append('"');
        }

        // Built-in: uptime is always reported
        json.append(',');
        json.append("\"uptime\":\"");
        json.append(formatUptime(uptimeMs));
        json.append('"');
        json.append('}');

        // Replace placeholder with actual status
        String result = json.toString();
        result = result.replaceFirst("\"status\":\"PLACEHOLDER\"",
            "\"status\":\"" + (allUp ? "UP" : "DOWN") + "\"");

        Response response = new Response()
            .ContentType("application/json")
            .data(result);
        if (allUp) {
            response.OK();
        } else {
            response.ServiceUnavailable();
        }
        return response;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatUptime(long ms) {
        long days = ms / 86_400_000;
        ms %= 86_400_000;
        long hours = ms / 3_600_000;
        ms %= 3_600_000;
        long minutes = ms / 60_000;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    static {
        Application.set("DOCUMENT_ROOT", null);
    }

    @Override
    public String getDocumentRoot() {
        while (documentRoot.trim().endsWith("/")) {
            documentRoot = documentRoot.trim().substring(0, documentRoot.trim().length() - 1);
        }
        return documentRoot;
    }

    @Override
    public void setTagsNamespace(String name) {
        HTMLEngine.setNamespace(name);
    }

    @Override
    public void addTagClass(String name, Class<?> cls) {
        try {
            HTMLEngine.addClass(name, cls);
        } catch (IOException iOException) {
            logger.log(Level.WARNING, "Failed to add tag class: " + name, iOException);
        }
    }

    @Override
    public void setDocumentRoot(String documentRoot) {
        this.documentRoot = documentRoot;
        if (documentRoot != null && !documentRoot.isEmpty()) {
            while (documentRoot.trim().endsWith("/")) {
                documentRoot = documentRoot.trim().substring(0, documentRoot.trim().length() - 1);
            }
        }
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

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void bind(InetSocketAddress address) throws Exception {
        bind(address, 50);
    }

    @Override
    public void bind(InetSocketAddress address, int backlog) throws Exception {
        this.address = address;
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocket = serverSocketChannel.socket();
        serverSocket.bind(address, backlog);
    }

    @Override
    public void addEndpoint(String path, Object endpoint) {
        documentationEndpoint.put(path + "?jsd", JsonServiceDescriptionEngine.generateServiceDefination(path, null, endpoint));
        registerEndpoint(pathMapping, path, endpoint);
    }

    @Override
    public void addEndpoint(String path, Object... endpoints) {
        for (Object endpoint : endpoints) {
            addEndpoint(path, endpoint);
        }
    }

    @Override
    public void addWebSocket(String path, Object handler) {
        registerWebSocketEndpoint(webSocketService, path, handler);
    }

    @Override
    public void addRequestFilter(HttpModule.RequestFilter filter) {
        if (filter != null) {
            requestFilters.add(filter);
        }
    }

    public WebSocketService getWebSocketService() {
        return webSocketService;
    }

    public void enableOpenApi(String specTitle, String specVersion) {
        enableOpenApi(specTitle, specVersion, "cookie");
    }

    public void enableOpenApi(String specTitle, String specVersion, String securityScheme, Class<?>... schemaClasses) {
        OpenApiService service = new OpenApiService(specTitle, specVersion, securityScheme,
                getRegisteredEndpoints(), schemaClasses);
        addEndpoint("/api", new OpenApiEndpoint(service));
    }

    public void enableMcp(Object... toolHandlers) {
        McpRegistry registry = new McpRegistry();
        for (Object handler : toolHandlers) {
            registry.register(handler);
        }
        registry.scanRegisteredEndpoints(getRegisteredEndpoints());
        addEndpoint("/api", new McpEndpoint(new McpDispatcher(registry)));
    }

    private void registerWebSocketEndpoint(WebSocketService service, String path, Object endpoint) {
        String wsPath = path;
        if (!wsPath.startsWith("/")) {
            wsPath = "/" + path;
        }
        if (wsPath.endsWith("/") && wsPath.length() > 1) {
            wsPath = wsPath.substring(0, wsPath.length() - 1);
        }
        service.register(wsPath, endpoint);
    }

    @Override
    public void connectedEvent(RestClient client) {
        if (connectionEvents != null) {
            connectionEvents.connected(client);
        }
    }

    @Override
    public void disconnectedEvent(RestClient client) {
        if (connectionEvents != null) {
            connectionEvents.disconnected(client);
        }
    }

    @Override
    public void requestEvent(RestClient client, RestHttpRequest request) {
        if (requestEvents != null) {
            requestEvents.request(client, request);
        }
    }

    @Override
    public void notifyEvent(String msg) {
        if (notificationEvents != null) {
            notificationEvents.notification(msg);
        }
    }

    @Override
    public void setConnectionEvents(HttpConnectionEvents connectionEvents) {
        this.connectionEvents = connectionEvents;
    }

    @Override
    public void setRequestEvents(HttpRequestEvents requestEvents) {
        this.requestEvents = requestEvents;
    }

    @Override
    public void setNotificationEvents(HttpNotificationEvents notificationEvents) {
        this.notificationEvents = notificationEvents;
    }

    @Override
    public boolean startServer() throws Exception {
        serverSocketChannel.configureBlocking(false);
        int validOps = serverSocketChannel.validOps();
        serverSocketChannel.register(selector, validOps, null);
        notifyEvent("Server start on " + address.toString());
        notifyEvent("Listering...");

        workerPool = new ThreadPoolExecutor(
            workerThreads, workerThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(maxQueueSize),
            new ThreadPoolExecutor.AbortPolicy()
        );

        // Freeze routing table for thread-safe reads from worker threads
        frozenPathMapping = freezePathMapping();

        long lastIdleSweep = System.currentTimeMillis();
        while (running) {
            processCompletedTasks();

            // Periodic idle connection sweep (every 30s)
            long now = System.currentTimeMillis();
            if (now - lastIdleSweep > 30_000) {
                sweepIdleConnections();
                lastIdleSweep = now;
            }

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
                        continue;
                    }

                    if (key.isReadable()) {
                        handleRead(key);
                        continue;
                    }

                    if (key.isWritable()) {
                        handleWrite(key);
                        continue;
                    }
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unhandled exception in NIO event loop", exception);
            } finally {
                selectedKeys.clear();
            }
            noop();
        }

        // --- Graceful shutdown ---
        shuttingDown = true;
        logger.log(Level.INFO, "Shutting down, draining in-flight requests (timeout={0}ms)", drainTimeoutMs);

        // Phase 1: Stop accepting new connections
        SelectionKey acceptKey = serverSocketChannel.keyFor(selector);
        if (acceptKey != null) {
            acceptKey.cancel();
        }

        // Phase 2: Wait for in-flight workers to complete
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(drainTimeoutMs, TimeUnit.MILLISECONDS)) {
                logger.log(Level.WARNING, "Worker pool did not drain within {0}ms, forcing shutdown", drainTimeoutMs);
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Worker pool shutdown interrupted, forcing shutdown", e);
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Phase 3: Drain completed tasks and flush pending writes
        processCompletedTasks();

        // Phase 4: Process writes and close remaining connections
        long drainDeadline = System.currentTimeMillis() + drainTimeoutMs;
        while (clientSessions.size() > 0 && System.currentTimeMillis() < drainDeadline) {
            processCompletedTasks();
            int amount = selector.select(100);
            if (amount > 0) {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                try {
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (!key.isValid()) {
                            continue;
                        }
                        if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Error during drain phase", e);
                } finally {
                    selectedKeys.clear();
                }
            }
        }

        // Phase 5: Force-close any remaining connections
        if (clientSessions.size() > 0) {
            logger.log(Level.WARNING, "Force-closing {0} remaining connections after drain timeout",
                clientSessions.size());
            for (SelectionKey key : selector.keys()) {
                try {
                    if (key.isValid() && key.channel() instanceof SocketChannel) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        key.cancel();
                        ch.close();
                    }
                } catch (Exception e) {
                    // Best effort
                }
            }
            clientSessions.clear();
        }

        // Phase 6: Close server resources
        try {
            selector.close();
        } catch (Exception e) {
            logger.log(Level.FINE, "Error closing selector", e);
        }
        try {
            serverSocketChannel.close();
        } catch (Exception e) {
            logger.log(Level.FINE, "Error closing server socket", e);
        }

        logger.log(Level.INFO, "Server shut down complete");
        return running;
    }

    /**
     * Closes connections that have been idle during read for too long.
     */
    private void sweepIdleConnections() {
        long now = System.currentTimeMillis();
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid() || !(key.channel() instanceof SocketChannel)) {
                continue;
            }
            // Read interest means the connection is waiting for data
            if (!key.isReadable()) {
                continue;
            }
            try {
                InternalClientSession session = (InternalClientSession) key.attachment();
                if (session == null || session.get("ws-mode") != null) {
                    continue; // Skip WebSocket connections
                }
                Object lastRead = session.get("last-read-time");
                if (lastRead instanceof Long) {
                    long idleMs = now - (Long) lastRead;
                    if (idleMs > idleReadTimeoutMs) {
                        logger.log(Level.FINE, "Closing idle connection: {0} (idle={1}ms)",
                            new Object[]{session.getSessionId(), idleMs});
                        handleDisconnect(key);
                    }
                }
            } catch (Exception e) {
                // Key might be in transition — skip
            }
        }
    }

    private void noop() {
        try {
            TimeUnit.NANOSECONDS.sleep(50);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleDisconnect(SelectionKey key) throws Exception {
        InternalClientSession clientSession = readAttachment(key);

        if (clientSession.get("ws-mode") != null) {
            webSocketService.handleDisconnect(clientSession);
        }

        key.cancel();
        SocketChannel client = (SocketChannel) key.channel();
        if (client.isOpen()) {
            client.close();
        }

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

        // WebSocket mode: write queued frames, keep connection open
        if (clientSession.get("ws-mode") != null) {
            SocketChannel client = (SocketChannel) key.channel();
            webSocketService.handleWrite(clientSession, client);
            return;
        }

        SocketChannel client = (SocketChannel) key.channel();
        Response response = clientSession.getResponse();
        ByteBuffer buffer = clientSession.getWriteBuffer();

        if (buffer == null) {
            boolean keepAlive = clientSession.get("request-connection") != null
                && "keep-alive".equalsIgnoreCase(clientSession.get("request-connection").toString());
            if (keepAlive) {
                response.header("Connection", "Keep-Alive");
            }

            // Apply gzip compression if client accepts it and response is eligible
            Object acceptEncoding = clientSession.get("request-accept-encoding");
            if (acceptEncoding != null && acceptEncoding.toString().contains("gzip")) {
                compressResponse(response);
            }

            buffer = ByteBuffer.wrap(response.build());
            clientSession.setWriteBuffer(buffer);
        }

        if (buffer.hasRemaining()) {
            int written = client.write(buffer);
            if (written == 0) {
                // Socket buffer full, wait for next OP_WRITE
                return;
            }
        }

        if (buffer.hasRemaining()) {
            // Partial write, keep OP_WRITE for next selector tick
            return;
        }

        // All bytes written — decide keep-alive or close
        clientSession.setWriteBuffer(null);

        boolean keepAlive = !shuttingDown && shouldKeepAlive(clientSession, response);
        if (keepAlive && client.isOpen()) {
            InternalClientSession newSession = new InternalClientSession(clientSession.getSessionId());
            client.register(selector, SelectionKey.OP_READ, newSession);
        } else {
            handleDisconnect(key);
        }
    }

    private boolean shouldKeepAlive(InternalClientSession clientSession, Response response) {
        // Respect explicit Connection header in the response
        // Default for HTTP/1.1 is keep-alive; we check the request headers via session
        String connectionHeader = clientSession.get("request-connection") != null
            ? clientSession.get("request-connection").toString() : null;
        if ("keep-alive".equalsIgnoreCase(connectionHeader)) {
            return true;
        }
        return false;
    }

    private void handleRead(SelectionKey key) throws Exception {
        InternalClientSession clientSession = readAttachment(key);
        SocketChannel client = (SocketChannel) key.channel();
        RestClient restClient = clientSessions.get(clientSession.getSessionId());

        // WebSocket mode: read raw bytes and dispatch frames
        if (clientSession.get("ws-mode") != null) {
            dataBuffer.clear();
            int read;
            try {
                read = client.read(dataBuffer);
            } catch (IOException e) {
                handleDisconnect(key);
                return;
            }
            if (read > 0) {
                dataBuffer.flip();
                byte[] bytes = new byte[read];
                dataBuffer.get(bytes);
                webSocketService.handleFrame(bytes, clientSession, key, client);
            } else if (read == -1) {
                handleDisconnect(key);
                return;
            }
            return;
        }

        dataBuffer.clear();
        String data = null;

        int read;
        int totalRead = 0;
        int MAX_BYTES = 1024 * 1024 * 5; // 5MB

        int readResult = client.read(dataBuffer);
        if (readResult == -1) {
            handleDisconnect(key);
            return;
        }
        if (readResult == 0) {
            return;
        }

        totalRead += readResult;
        dataBuffer.flip();
        byte[] bytes = new byte[dataBuffer.limit()];
        dataBuffer.get(bytes);
        data = new String(bytes);
        dataBuffer.clear();

        // Read any additional available data
        while ((read = client.read(dataBuffer)) > 0) {
            totalRead += read;
            if (totalRead > MAX_BYTES) {
                break;
            }

            dataBuffer.flip();
            bytes = new byte[dataBuffer.limit()];
            dataBuffer.get(bytes);
            data += new String(bytes);
            dataBuffer.clear();
        }

        if (data != null && data.length() > 0) {
            notifyEvent(data);
            clientSession.add("last-read-time", System.currentTimeMillis());
            RestHttpRequest request = parseRequest(restClient, client, data);

            // Health check bypass — runs inline on event loop, no worker pool
            if (request.getMethod().equals("GET") && request.getContextPath().equals(healthPath)) {
                Response healthResponse = executeHealthCheck();
                healthResponse.header("Connection", "close");
                InternalClientSession healthSession = new InternalClientSession(
                    clientSession.getSessionId(), healthResponse);
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(healthSession);
                return;
            }

            // HTTP/1.1 requires Host header (RFC 7230 §5.4)
            String hostHeader = request.getHeader("Host");
            if (hostHeader == null || hostHeader.trim().isEmpty()) {
                Response badRequest = new Response().BadRequest().data("HTTP/1.1 requires Host header");
                InternalClientSession errorSession = new InternalClientSession(
                    clientSession.getSessionId(), badRequest);
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(errorSession);
                return;
            }

            // Store Connection header for keep-alive decision in handleWrite
            String connectionHeader = request.getHeader("Connection");
            if (connectionHeader != null) {
                clientSession.add("request-connection", connectionHeader);
            }

            // Store Accept-Encoding for optional gzip compression in handleWrite
            String acceptEncoding = request.getHeader("Accept-Encoding");
            if (acceptEncoding != null) {
                clientSession.add("request-accept-encoding", acceptEncoding);
            }

            // Check for WebSocket upgrade before normal routing
            if (webSocketService.isWebSocketUpgrade(request)) {
                webSocketService.handleUpgrade(request, client, key, clientSession);
                return;
            }

            // Create a worker session and offload endpoint processing to the thread pool
            key.interestOps(0);
            InternalClientSession workerSession = new InternalClientSession(clientSession.getSessionId());
            workerSession.add("request-start-time", System.currentTimeMillis());
            if (connectionHeader != null) {
                workerSession.add("request-connection", connectionHeader);
            }
            if (acceptEncoding != null) {
                workerSession.add("request-accept-encoding", acceptEncoding);
            }
            try {
                workerPool.submit(new WorkerTask(key, workerSession, request));
            } catch (RejectedExecutionException e) {
                // Back-pressure: pool queue full, return 503 immediately
                Response overloadResponse = new Response().ServiceUnavailable();
                InternalClientSession errorSession = new InternalClientSession(
                    clientSession.getSessionId(), overloadResponse);
                if (connectionHeader != null) {
                    errorSession.add("request-connection", connectionHeader);
                }
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(errorSession);
                selector.wakeup();
            }
        }

    }

    private void processCompletedTasks() {
        SelectionKey completedKey;
        while ((completedKey = completionQueue.poll()) != null) {
            if (!completedKey.isValid()) {
                // Client disconnected before worker finished; discard
                continue;
            }
            InternalClientSession workerSession = (InternalClientSession) completedKey.attachment();
            Response response = workerSession.getResponse();
            if (response == null) {
                // Safety: worker failed to set response
                response = new Response().InternalServerError();
            }
            InternalClientSession writeSession = new InternalClientSession(
                workerSession.getSessionId(), response);
            // Preserve the connection header for keep-alive decision
            Object connHeader = workerSession.get("request-connection");
            if (connHeader != null) {
                writeSession.add("request-connection", connHeader);
            }
            // Preserve Accept-Encoding for gzip compression in handleWrite
            Object acceptEncoding = workerSession.get("request-accept-encoding");
            if (acceptEncoding != null) {
                writeSession.add("request-accept-encoding", acceptEncoding);
            }
            try {
                completedKey.attach(writeSession);
                completedKey.interestOps(SelectionKey.OP_WRITE);
            } catch (Exception e) {
                // Key was cancelled/closed between isValid() check and now
                logger.log(Level.FINE, "Key cancelled between isValid and attach in processCompletedTasks", e);
            }
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
        String rawPost = "";
        if (indexOfFormdata != -1 && headerData.length() >= indexOfFormdata + 4) {
            rawPost = headerData.substring(indexOfFormdata + 4);
        }

        if (indexOfFormdata == -1) {
            indexOfFormdata = headerData.indexOf("\n\n");
            if (indexOfFormdata != -1 && headerData.length() >= indexOfFormdata + 2) {
                rawPost = headerData.substring(indexOfFormdata + 2);
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
        String subdomain = headers.get("HOST");
        if (subdomain != null && subdomain.contains(".")) {
            subdomain = subdomain.substring(0, subdomain.indexOf("."));
            httpRequest.addHeader("SUBDOMAIN", subdomain);
        }

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
        if ((contextPath.trim().equals("") || contextPath.trim().equals("/")) && request.getQueryStrings().containsKey("jsd")) {
            API api = new API();
            for (Service service : documentationEndpoint.values()) {
                api.addService(service);
            }
            return new Response().OK().ContentType("application/json").model(api);

        }

        if (documentationEndpoint.containsKey(contextPath)) {
            return new Response().OK().ContentType("application/json").model(documentationEndpoint.get(contextPath));
        }

        if (contextPath.equals("")) {
            contextPath = "/index.htm";
            if (!isRequestADocument(contextPath)) {
                contextPath = "/index.html";
            }
        }

        if (isRequestADocument(contextPath) && Application.get("DOCUMENT_ROOT") != null) {
            File documentFile = new File(getDocumentRoot() + contextPath);
            if (contextPath.toLowerCase().endsWith(".htm") || contextPath.toLowerCase().endsWith(".html")) {
                Map<String, String> properties = new TreeMap<>();
                properties.putAll(request.getPostParams());
                properties.putAll(request.getQueryStrings());

                Map<String, Object> resources = new TreeMap<>();
                resources.put(RestHttpRequest.class.getName(), request);

                String contentData = HTMLEngine.process(documentFile, properties, resources);
                return new Response().OK().ContentType("text/html").data(contentData);
            }

            String fileExt = contextPath.substring(contextPath.lastIndexOf(".") + 1);
            String contextType = MimeTypes.ContentType(fileExt);

            byte[] fileContent = Files.readAllBytes(documentFile.toPath());
            return new Response().OK().ContentType(contextType).Content(fileContent);
        }

        Map<String, RestEndpoint> requestMap = pathMapping.get(request.getMethod().toLowerCase());
        RestActionService restAction = locateEndpoint(requestMap, contextPath.trim());

        if (restAction != null) {
            Response filteredResponse = applyRequestFilters(request, restAction.getEndpoint());
            if (filteredResponse != null) {
                return filteredResponse;
            }
            return restAction.process(request);
        } else {
            return new Response().NotFound();
        }
    }

    private Response applyRequestFilters(RestHttpRequest request, RestEndpoint endpoint) {
        HttpModule.EndpointInfo endpointInfo = new HttpModule.EndpointInfo(endpoint.getPath(), endpoint.getMethod(), endpoint.getObject());
        for (HttpModule.RequestFilter filter : requestFilters) {
            Response response = filter.filter(request, endpointInfo);
            if (response != null) {
                return response;
            }
        }
        return null;
    }

    private RestActionService locateEndpoint(Map<String, RestEndpoint> mapping, String contextPath) throws Exception {
        if (mapping.containsKey(contextPath)) {
            return new RestActionService(mapping.get(contextPath), new LinkedList<>());
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
                return new RestActionService(mapping.get(pattern), arguments);
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

    /**
     * Compresses the response body with gzip if eligible.
     * Adds Content-Encoding: gzip and updates Content-Length.
     */
    private static void compressResponse(Response response) {
        if (!shouldCompress(response)) {
            return;
        }
        try {
            byte[] original = response.getContent();
            if (original.length < 1024) {
                return;  // Don't compress small responses
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(original);
            gzip.finish();
            gzip.close();
            byte[] compressed = bos.toByteArray();
            response.Content(compressed);
            response.header("Content-Encoding", "gzip");
        } catch (Exception e) {
            logger.log(Level.FINE, "Gzip compression failed, sending uncompressed", e);
        }
    }

    /**
     * Returns true if the response content type is compressible.
     */
    private static boolean shouldCompress(Response response) {
        // Don't compress chunked responses (would need streaming gzip)
        if (response.isChunked()) {
            return false;
        }
        String ct = response.getContentType();
        if (ct == null) {
            return false;
        }
        ct = ct.toLowerCase();
        // Compress text, JSON, XML, JavaScript, CSS, HTML, SVG, and font formats
        return ct.contains("text/")
            || ct.contains("/json")
            || ct.contains("+xml")
            || ct.contains("/xml")
            || ct.contains("javascript")
            || ct.contains("/css")
            || ct.contains("/html")
            || ct.contains("/svg")
            || ct.contains("application/x-www-form-urlencoded");
    }

    /**
     * Creates an immutable deep copy of the path mapping for thread-safe reads
     * from worker threads. Must be called after all endpoints are registered.
     */
    private Map<String, Map<String, RestEndpoint>> freezePathMapping() {
        Map<String, Map<String, RestEndpoint>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, RestEndpoint>> entry : pathMapping.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private class WorkerTask implements Runnable {
        private final SelectionKey key;
        private final InternalClientSession clientSession;
        private final RestHttpRequest request;

        WorkerTask(SelectionKey key, InternalClientSession clientSession, RestHttpRequest request) {
            this.key = key;
            this.clientSession = clientSession;
            this.request = request;
        }

        @Override
        public void run() {
            Response response;

            // Check request timeout before processing
            Object startTimeObj = clientSession.get("request-start-time");
            long startTime = (startTimeObj instanceof Long) ? (Long) startTimeObj : System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - startTime;
            int effectiveTimeout = resolveTimeout(request);

            if (effectiveTimeout > 0 && elapsed > effectiveTimeout) {
                logger.log(Level.FINE, "Request timeout: {0} {1} (elapsed={2}ms, timeout={3}ms)",
                    new Object[]{request.getMethod(), request.getContextPath(), elapsed, effectiveTimeout});
                response = new Response().RequestTimeout().ContentType("text/plain").data("Request Timeout");
                clientSession.setResponse(response);
                key.attach(clientSession);
                completionQueue.add(key);
                selector.wakeup();
                return;
            }

            try {
                response = handleRequestMapping(frozenPathMapping, request);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Worker task failed for " + request.getMethod() + " " + request.getContextPath(), e);
                try {
                    response = new Response().InternalServerError().data(e.toString());
                } catch (Exception inner) {
                    logger.log(Level.WARNING, "Failed to build 500 error response", inner);
                    response = new Response().InternalServerError();
                }
            }
            clientSession.setResponse(response);
            key.attach(clientSession);
            completionQueue.add(key);
            selector.wakeup();
        }

        /**
         * Resolves the effective request timeout for this request.
         * Checks the endpoint's {@code @Timeout} annotation first,
         * then falls back to the global {@code defaultRequestTimeoutMs}.
         * Returns 0 if no timeout should be applied.
         */
        private int resolveTimeout(RestHttpRequest request) {
            // Try to look up the endpoint to check for @Timeout annotation
            Map<String, RestEndpoint> methodMap = frozenPathMapping.get(request.getMethod().toLowerCase());
            if (methodMap != null) {
                // Exact match
                RestEndpoint endpoint = methodMap.get(request.getContextPath());
                if (endpoint != null) {
                    Timeout timeout = endpoint.getMethod().getAnnotation(Timeout.class);
                    if (timeout == null) {
                        timeout = endpoint.getObject().getClass().getAnnotation(Timeout.class);
                    }
                    if (timeout != null) {
                        return timeout.ms();
                    }
                }
                // Try regex patterns for parameterized routes
                if (endpoint == null) {
                    for (Map.Entry<String, RestEndpoint> entry : methodMap.entrySet()) {
                        if (entry.getKey().startsWith("^")) {
                            Pattern p = Pattern.compile(entry.getKey());
                            if (p.matcher(request.getContextPath()).matches()) {
                                RestEndpoint ep = entry.getValue();
                                Timeout timeout = ep.getMethod().getAnnotation(Timeout.class);
                                if (timeout == null) {
                                    timeout = ep.getObject().getClass().getAnnotation(Timeout.class);
                                }
                                if (timeout != null) {
                                    return timeout.ms();
                                }
                                break;
                            }
                        }
                    }
                }
            }
            return defaultRequestTimeoutMs;
        }
    }

}
