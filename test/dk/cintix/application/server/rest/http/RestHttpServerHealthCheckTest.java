package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.HealthCheck;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RestHttpServerHealthCheckTest {

    // Simple probe for testing
    private static class StubHealthCheck implements HealthCheck {
        private final String name;
        private final String result;
        StubHealthCheck(String name, String result) { this.name = name; this.result = result; }
        @Override public String name() { return name; }
        @Override public String check() { return result; }
    }

    public void runAll() {
        healthEndpoint_returns200WhenAllUp();
        healthEndpoint_returns503WhenAnyDown();
        healthEndpoint_bypassesWorkerPool();
        healthEndpoint_includesUptime();
    }

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
                if (sb.toString().contains("\r\n\r\n") && sb.toString().endsWith("}")) break;
            } else {
                Thread.sleep(10);
            }
        }
        socket.close();
        return sb.toString();
    }

    // --- Tests ---

    public void healthEndpoint_returns200WhenAllUp() {
        try {
            RestHttpServer server = new RestHttpServer() {};
            server.addHealthCheck(new StubHealthCheck("database", "UP"));
            server.addHealthCheck(new StubHealthCheck("disk", "UP"));
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            String response = sendRequest(port,
                "GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            TestSupport.assertTrue(response.contains("200 OK"),
                "Should return 200 OK, got: " + response.substring(0, Math.min(response.length(), 150)));
            TestSupport.assertTrue(response.contains("\"status\":\"UP\""),
                "Status should be UP");
            TestSupport.assertTrue(response.contains("\"database\":\"UP\""),
                "Database should be UP");
            TestSupport.assertTrue(response.contains("\"disk\":\"UP\""),
                "Disk should be UP");

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("healthEndpoint_returns200WhenAllUp failed", e);
        }
    }

    public void healthEndpoint_returns503WhenAnyDown() {
        try {
            RestHttpServer server = new RestHttpServer() {};
            server.addHealthCheck(new StubHealthCheck("database", "UP"));
            server.addHealthCheck(new StubHealthCheck("redis", "Connection refused"));
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            String response = sendRequest(port,
                "GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            TestSupport.assertTrue(response.contains("503 Service Unavailable"),
                "Should return 503 when a check is DOWN, got: " +
                    response.substring(0, Math.min(response.length(), 150)));
            TestSupport.assertTrue(response.contains("\"status\":\"DOWN\""),
                "Status should be DOWN");
            TestSupport.assertTrue(response.contains("Connection refused"),
                "Failed check message should be in response");

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("healthEndpoint_returns503WhenAnyDown failed", e);
        }
    }

    public void healthEndpoint_bypassesWorkerPool() {
        try {
            // Configure tiny pool — health should still respond because it bypasses
            RestHttpServer server = new RestHttpServer() {};
            server.setMaxQueueSize(1);
            server.setWorkerThreads(1);
            server.addHealthCheck(new StubHealthCheck("test", "UP"));
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Health check must respond even with minimal pool config
            String response = sendRequest(port,
                "GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            TestSupport.assertTrue(response.contains("200 OK") || response.contains("503"),
                "Health check should respond, got: " +
                    response.substring(0, Math.min(response.length(), 150)));
            TestSupport.assertTrue(response.contains("\"status\":\""),
                "Health check should return valid JSON with status");

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("healthEndpoint_bypassesWorkerPool failed", e);
        }
    }

    public void healthEndpoint_includesUptime() {
        try {
            RestHttpServer server = new RestHttpServer() {};
            server.bind(new InetSocketAddress(0));
            Thread t = new Thread(() -> { try { server.startServer(); } catch (Exception e) {} });
            t.setDaemon(true);
            t.start();
            Thread.sleep(100);
            int port = server.getPort();

            String response = sendRequest(port,
                "GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            TestSupport.assertTrue(response.contains("\"uptime\":\""),
                "Health check should include uptime: " +
                    response.substring(0, Math.min(response.length(), 200)));

            server.setRunning(false);
            t.join(3000);
        } catch (Exception e) {
            throw new RuntimeException("healthEndpoint_includesUptime failed", e);
        }
    }
}
