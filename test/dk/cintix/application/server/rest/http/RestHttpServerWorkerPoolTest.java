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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RestHttpServerWorkerPoolTest {

    public void runAll() {
        concurrentRequests_doNotBlockEachOther();
        workerException_returns500();
        queueFull_returns503();
    }

    // --- Test Endpoints ---

    public static class TestEndpoint {
        @GET
        @Action(path = "/fast")
        public Response fast() {
            return new Response().OK().ContentType("text/plain").data("fast");
        }

        @GET
        @Action(path = "/slow")
        public Response slow() throws Exception {
            Thread.sleep(1500);
            return new Response().OK().ContentType("text/plain").data("slow");
        }

        @GET
        @Action(path = "/error")
        public Response error() {
            throw new RuntimeException("Intentional worker error");
        }
    }

    // --- Server Helpers ---

    private static RestHttpServer createServer() throws Exception {
        RestHttpServer server = new RestHttpServer() {};
        server.addEndpoint("", new TestEndpoint());
        server.bind(new InetSocketAddress(0));
        return server;
    }

    private static Thread startServer(final RestHttpServer server) {
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.startServer();
                } catch (Exception e) {
                    // Server stopped
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        return serverThread;
    }

    private static String sendRequest(int port, String httpRequest) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(httpRequest.getBytes());
        out.flush();

        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 5000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) break;
                sb.append(new String(buf, 0, read));
                // Check for complete response
                String sofar = sb.toString();
                int headerEnd = sofar.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
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
        socket.close();
        return sb.toString();
    }

    // --- Test Cases ---

    public void concurrentRequests_doNotBlockEachOther() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act -- send a slow request and a fast request concurrently
            final String[] slowResult = new String[1];
            final String[] fastResult = new String[1];
            Thread slowThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        slowResult[0] = sendRequest(port,
                            "GET /slow HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
                    } catch (Exception e) {
                        slowResult[0] = "ERROR: " + e.getMessage();
                    }
                }
            });
            Thread fastThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        fastResult[0] = sendRequest(port,
                            "GET /fast HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
                    } catch (Exception e) {
                        fastResult[0] = "ERROR: " + e.getMessage();
                    }
                }
            });

            long start = System.currentTimeMillis();
            slowThread.start();
            // Give the slow request time to be accepted before sending fast
            Thread.sleep(50);
            fastThread.start();

            fastThread.join(3000);
            slowThread.join(5000);
            long elapsed = System.currentTimeMillis() - start;

            // Assert
            TestSupport.assertTrue(elapsed < 2500,
                "Fast request should complete before slow request finishes (elapsed=" + elapsed + "ms)");
            TestSupport.assertTrue(fastResult[0] != null && fastResult[0].contains("200 OK"),
                "Fast request should return 200 OK, got: " +
                    (fastResult[0] != null ? fastResult[0].substring(0, Math.min(fastResult[0].length(), 200)) : "null"));
            TestSupport.assertTrue(fastResult[0].contains("fast"),
                "Fast request should contain 'fast' body");
            TestSupport.assertTrue(slowResult[0] != null && slowResult[0].contains("200 OK"),
                "Slow request should return 200 OK after completion, got: " +
                    (slowResult[0] != null ? slowResult[0].substring(0, Math.min(slowResult[0].length(), 200)) : "null"));
            TestSupport.assertTrue(slowResult[0].contains("slow"),
                "Slow request should contain 'slow' body");

            // Cleanup
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("concurrentRequests_doNotBlockEachOther failed", e);
        }
    }

    public void workerException_returns500() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET /error HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("500 Internal Server Error"),
                "Exception in worker should return 500, got: " +
                    response.substring(0, Math.min(response.length(), 100)));

            // Cleanup
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("workerException_returns500 failed", e);
        }
    }

    public void queueFull_returns503() {
        // Arrange
        try {
            final RestHttpServer server = new RestHttpServer() {};
            server.addEndpoint("", new TestEndpoint());
            server.setMaxQueueSize(1);  // Tiny queue
            server.bind(new InetSocketAddress(0));
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act -- send many concurrent requests to overwhelm the pool queue
            int requestCount = 20;
            final String[] results = new String[requestCount];
            Thread[] threads = new Thread[requestCount];
            final CountDownLatch latch = new CountDownLatch(requestCount);
            for (int i = 0; i < requestCount; i++) {
                final int idx = i;
                threads[i] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String result = sendRequest(port,
                                "GET /slow HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
                            synchronized (results) {
                                results[idx] = result;
                            }
                        } catch (Exception e) {
                            synchronized (results) {
                                results[idx] = "ERROR: " + e.getMessage();
                            }
                        } finally {
                            latch.countDown();
                        }
                    }
                });
                threads[i].start();
                Thread.sleep(10); // stagger connections to avoid socket backlog
            }

            boolean allDone = latch.await(10, TimeUnit.SECONDS);

            // Assert
            TestSupport.assertTrue(allDone, "All requests should complete within timeout");
            int success503 = 0;
            int success200 = 0;
            for (int i = 0; i < requestCount; i++) {
                String result = results[i];
                if (result != null && result.contains("503 Service Unavailable")) {
                    success503++;
                } else if (result != null && result.contains("200 OK")) {
                    success200++;
                }
            }
            TestSupport.assertTrue(success503 > 0,
                "At least one request should get 503 when queue is full (got 503=" + success503 +
                " 200=" + success200 + ")");
            TestSupport.assertTrue(success200 > 0,
                "At least some requests should get 200 OK (got 503=" + success503 +
                " 200=" + success200 + ")");

            // Cleanup
            server.setRunning(false);
            serverThread.join(2000);

        } catch (Exception e) {
            throw new RuntimeException("queueFull_returns503 failed", e);
        }
    }
}
