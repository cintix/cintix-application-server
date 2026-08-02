package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.GET;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.modules.http.server.HttpModule;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Tests for CAS v3.0.2 root path bug fixes:
 *
 *   1. @Action(path = "/") should match GET /
 *   2. RequestFilter should run for ALL requests, even those without a matching endpoint
 *   3. Endpoints should have priority over static file serving
 *   4. Static file directory listings should not break endpoint matching
 */
public class RestHttpServerRootPathTest {

    // --- Endpoint with @Action(path = "/") ---

    public static class RootEndpoint {
        @GET
        @Action(path = "/")
        public Response root() {
            return new Response().OK().ContentType("text/plain").data("root endpoint");
        }

        @GET
        @Action(path = "/app")
        public Response app() {
            return new Response().OK().ContentType("text/plain").data("app endpoint");
        }
    }

    // --- Helper methods ---

    private String sendRequest(int port, String httpRequest) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(httpRequest.getBytes());
        out.flush();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 3000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) break;
                sb.append(new String(buf, 0, read));
                String sofar = sb.toString();
                int headerEnd = sofar.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    int contentLength = 0;
                    String headers = sofar.substring(0, headerEnd);
                    for (String line : headers.split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                            break;
                        }
                    }
                    if (sofar.length() >= headerEnd + 4 + contentLength) {
                        break;
                    }
                }
            } else {
                Thread.sleep(10);
            }
        }
        socket.close();
        return sb.toString();
    }

    // --- Tests ---

    public void runAll() {
        rootPath_actionSlash_returns200();
        rootPath_actionSlash_withExplicitBaseSlash_returns200();
        requestFilter_runsForAllRequests_evenWithoutMatchingEndpoint();
        endpointHasPriority_overStaticFileServing();
        directoryPath_doesNotBreakEndpointMatching();
    }

    /**
     * Issue #1: @Action(path = "/") should match GET /
     * Registered with addEndpoint("", endpoint) — base is empty string.
     */
    public void rootPath_actionSlash_returns200() {
        try {
            // Arrange
            RestHttpServer server = new RestHttpServer() {};
            server.addEndpoint("", new RootEndpoint());
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                "GET / should return 200 OK, got: " +
                    response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("root endpoint"),
                "GET / should return 'root endpoint' body, got: " +
                    response.substring(0, Math.min(response.length(), 300)));

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("rootPath_actionSlash_returns200 failed", e);
        }
    }

    /**
     * Issue #1 variant: @Action(path = "/") with addEndpoint("/", endpoint).
     * The base is "/" so the registered key is "/" (not "").
     */
    public void rootPath_actionSlash_withExplicitBaseSlash_returns200() {
        try {
            // Arrange
            RestHttpServer server = new RestHttpServer() {};
            server.addEndpoint("/", new RootEndpoint());
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                "GET / with base=/ should return 200 OK, got: " +
                    response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("root endpoint"),
                "GET / with base=/ should return 'root endpoint' body");

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("rootPath_actionSlash_withExplicitBaseSlash_returns200 failed", e);
        }
    }

    /**
     * Issue #2: RequestFilter should run for ALL requests, even those without
     * a matching endpoint. The filter receives null EndpointInfo for unmatched paths.
     */
    public void requestFilter_runsForAllRequests_evenWithoutMatchingEndpoint() {
        try {
            // Arrange
            RestHttpServer server = new RestHttpServer() {};
            // No endpoints registered — /nowhere does not exist
            server.bind(new InetSocketAddress(0));

            // Register a filter that intercepts /nowhere
            server.addRequestFilter(new HttpModule.RequestFilter() {
                @Override
                public Response filter(RestHttpRequest request,
                                       HttpModule.EndpointInfo endpointInfo) {
                    if (request.getContextPath().equals("/nowhere")) {
                        // Verify EndpointInfo is null for unmatched paths
                        TestSupport.assertNull(endpointInfo,
                            "EndpointInfo should be null for unmatched path");
                        return new Response().OK().ContentType("text/plain").data("filter handled it");
                    }
                    return null;
                }
            });

            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET /nowhere HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                "Filter should intercept /nowhere with 200 OK, got: " +
                    response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("filter handled it"),
                "Filter should return custom body for unmatched path");

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("requestFilter_runsForAllRequests_evenWithoutMatchingEndpoint failed", e);
        }
    }

    /**
     * Issue #3: Endpoints should have priority over static file serving.
     * Even when web/index.html exists, @Action(path = "/") should win.
     */
    public void endpointHasPriority_overStaticFileServing() {
        try {
            // Arrange — create a temporary web directory with index.html
            File webDir = new File("web");
            boolean createdDir = webDir.mkdirs();
            File indexFile = new File("web/index.html");
            java.nio.file.Files.write(indexFile.toPath(), "<html>static</html>".getBytes());

            RestHttpServer server = new RestHttpServer() {};
            server.addEndpoint("", new RootEndpoint());
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert — endpoint should win, not static file
            TestSupport.assertTrue(response.contains("200 OK"),
                "GET / should return 200 OK, got: " +
                    response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("root endpoint"),
                "Endpoint should have priority over static index.html, got: " +
                    response.substring(0, Math.min(response.length(), 300)));
            TestSupport.assertTrue(!response.contains("<html>static</html>"),
                "Static index.html should NOT be served when endpoint exists");

            // Cleanup
            server.setRunning(false);
            t.join(3000);
            indexFile.delete();
            if (createdDir) webDir.delete();
        } catch (Exception e) {
            throw new RuntimeException("endpointHasPriority_overStaticFileServing failed", e);
        }
    }

    /**
     * Issue #4: Static file directory listings should not break endpoint matching.
     * If web/app/ exists as a directory, @Action(path = "/app") should still work
     * and return the endpoint response, not 500 IOException "Is a directory".
     */
    public void directoryPath_doesNotBreakEndpointMatching() {
        try {
            // Arrange — create a web/app/ directory (no index file)
            File webDir = new File("web");
            boolean createdWebDir = webDir.mkdirs();
            File appDir = new File("web/app");
            boolean createdAppDir = appDir.mkdirs();

            RestHttpServer server = new RestHttpServer() {};
            server.addEndpoint("", new RootEndpoint());
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET /app HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert — should return 200 from endpoint, not 500
            TestSupport.assertTrue(response.contains("200 OK"),
                "GET /app should return 200 OK when endpoint exists, got: " +
                    response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("app endpoint"),
                "Endpoint should handle /app even when web/app/ directory exists, got: " +
                    response.substring(0, Math.min(response.length(), 300)));
            TestSupport.assertTrue(!response.contains("500"),
                "Should not return 500 when directory with same name exists");

            // Cleanup
            server.setRunning(false);
            t.join(3000);
            appDir.delete();
            if (createdWebDir) {
                // Only delete web/ if it's empty (we created it)
                webDir.delete();
            }
        } catch (Exception e) {
            throw new RuntimeException("directoryPath_doesNotBreakEndpointMatching failed", e);
        }
    }
}
