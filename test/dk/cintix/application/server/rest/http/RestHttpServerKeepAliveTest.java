package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.GET;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RestHttpServerKeepAliveTest {

    public void runAll() {
        keepAlive_twoRequestsOnSameConnection_bothSucceed();
        keepAlive_multipleRequests_reuseConnection();
        connectionClose_closesAfterResponse();
    }

    public static class TestEndpoint {
        @GET
        @Action(path = "/hello")
        public Response hello() {
            return new Response().OK().ContentType("text/plain").data("Hello World");
        }

        @GET
        @Action(path = "/count")
        public Response count() {
            return new Response().OK().ContentType("text/plain").data("Count: " + counter);
        }

        static int counter = 0;
    }

    private static RestHttpServer createServer() throws Exception {
        RestHttpServer server = new RestHttpServer() {};
        server.addEndpoint("", new TestEndpoint());
        server.bind(new InetSocketAddress(0));
        return server;
    }

    private static Thread startServer(final RestHttpServer server) {
        Thread serverThread = new Thread(() -> {
            try {
                server.startServer();
            } catch (Exception e) {
                // Server stopped
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        // Give the server time to start
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        return serverThread;
    }

    private static String readResponse(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int totalRead = 0;
        long start = System.currentTimeMillis();
        while (totalRead < 8192 && (System.currentTimeMillis() - start) < 5000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) break;
                sb.append(new String(buf, 0, read));
                totalRead += read;
                // Check if we have a complete HTTP response (headers end with \r\n\r\n)
                String sofar = sb.toString();
                int headerEnd = sofar.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    // Try to read body based on Content-Length
                    int bodyStart = headerEnd + 4;
                    int contentLength = 0;
                    String headers = sofar.substring(0, headerEnd);
                    for (String line : headers.split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                            break;
                        }
                    }
                    if (sofar.length() >= bodyStart + contentLength) {
                        break;
                    }
                }
            } else {
                Thread.sleep(10);
            }
        }
        return sb.toString();
    }

    public void keepAlive_twoRequestsOnSameConnection_bothSucceed() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            Socket socket = new Socket("127.0.0.1", port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Act — first request
            String request1 = "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: keep-alive\r\n\r\n";
            out.write(request1.getBytes());
            out.flush();
            String response1 = readResponse(in);

            // Act — second request on same connection
            String request2 = "GET /count HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: keep-alive\r\n\r\n";
            out.write(request2.getBytes());
            out.flush();
            String response2 = readResponse(in);

            // Assert
            TestSupport.assertTrue(response1.contains("200 OK"), "First response should be 200 OK, was: " + response1.substring(0, Math.min(response1.length(), 200)));
            TestSupport.assertTrue(response1.contains("Hello World"), "First response should contain body");
            TestSupport.assertTrue(response1.toLowerCase().contains("keep-alive"), "First response should be keep-alive");

            TestSupport.assertTrue(response2.contains("200 OK"), "Second response should be 200 OK, was: " + response2.substring(0, Math.min(response2.length(), 200)));
            TestSupport.assertTrue(response2.contains("Count:"), "Second response should contain body");
            TestSupport.assertTrue(response2.toLowerCase().contains("keep-alive"), "Second response should be keep-alive");

            // Cleanup
            socket.close();
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("keepAlive_twoRequestsOnSameConnection_bothSucceed failed", e);
        }
    }

    public void keepAlive_multipleRequests_reuseConnection() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            Socket socket = new Socket("127.0.0.1", port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Act — send 3 requests on the same connection
            int successCount = 0;
            for (int i = 0; i < 3; i++) {
                String request = "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: keep-alive\r\n\r\n";
                out.write(request.getBytes());
                out.flush();
                String response = readResponse(in);
                if (response.contains("200 OK") && response.contains("Hello World")) {
                    successCount++;
                }
            }

            // Assert
            TestSupport.assertEquals(3, successCount, "All 3 requests on same connection should succeed");

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("keepAlive_multipleRequests_reuseConnection failed", e);
        }
    }

    public void connectionClose_closesAfterResponse() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            Socket socket = new Socket("127.0.0.1", port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Act — send request WITHOUConnection: keep-alive
            String request = "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n";
            out.write(request.getBytes());
            out.flush();
            String response = readResponse(in);

            // Assert — response should have Connection: Closed (default)
            TestSupport.assertTrue(response.contains("200 OK"), "Response should be 200 OK");
            TestSupport.assertTrue(response.contains("Hello World"), "Response should contain body");

            // Try to read more — connection should be closed by server
            boolean closed = false;
            try {
                // Small delay for server to close
                Thread.sleep(200);
                int read = in.read();
                if (read == -1) {
                    closed = true;
                }
            } catch (Exception e) {
                closed = true;
            }
            TestSupport.assertTrue(closed, "Connection should be closed when keep-alive not requested");

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("connectionClose_closesAfterResponse failed", e);
        }
    }
}
