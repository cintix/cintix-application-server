package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.GET;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.zip.GZIPInputStream;

public class RestHttpServerHttp11ComplianceTest {

    public void runAll() {
        missingHostHeader_returns400();
        gzipCompression_compressesResponseBody();
        noAcceptEncoding_returnsUncompressed();
        smallResponse_skipsGzip();
        chunkedEncoding_producesValidChunkedResponse();
    }

    // --- Test Endpoints ---

    public static class TestEndpoint {
        @GET
        @Action(path = "/hello")
        public Response hello() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("Hello World! This is a longer response to test gzip compression. ");
            }
            return new Response().OK().ContentType("text/plain").data(sb.toString());
        }

        @GET
        @Action(path = "/tiny")
        public Response tiny() {
            return new Response().OK().ContentType("text/plain").data("hi");
        }

        @GET
        @Action(path = "/chunked")
        public Response chunked() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("Chunk ").append(i).append(" data. ");
            }
            return new Response().OK().ContentType("text/plain").data(sb.toString()).chunked();
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
        byte[] buf = new byte[65536];
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 5000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) break;
                sb.append(new String(buf, 0, read));
                String sofar = sb.toString();
                int headerEnd = sofar.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    int bodyStart = headerEnd + 4;
                    int contentLength = -1;
                    boolean chunked = false;
                    String headers = sofar.substring(0, headerEnd);
                    for (String line : headers.split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                        if (line.toLowerCase().startsWith("transfer-encoding: chunked")) {
                            chunked = true;
                        }
                    }
                    if (chunked) {
                        // Read until terminating chunk
                        if (sofar.contains("0\r\n\r\n")) {
                            break;
                        }
                    } else if (contentLength >= 0 && sofar.length() >= bodyStart + contentLength) {
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

    // --- Helper ---

    /**
     * Sends a request and returns raw bytes for binary-safe body handling.
     */
    private static RawResponse sendRequestRaw(int port, String httpRequest) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(httpRequest.getBytes());
        out.flush();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        long start = System.currentTimeMillis();
        int totalRead = 0;
        while ((System.currentTimeMillis() - start) < 5000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) break;
                bos.write(buf, 0, read);
                totalRead += read;
                // Parse headers from accumulated data
                byte[] data = bos.toByteArray();
                String headerStr = new String(data, 0, Math.min(data.length, 8192), "ISO-8859-1");
                int headerEnd = headerStr.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    int bodyStart = headerEnd + 4;
                    int contentLength = -1;
                    boolean chunked = false;
                    String headers = headerStr.substring(0, headerEnd);
                    for (String line : headers.split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                        if (line.toLowerCase().startsWith("transfer-encoding: chunked")) {
                            chunked = true;
                        }
                    }
                    if (chunked) {
                        if (headerStr.contains("0\r\n\r\n")) {
                            break;
                        }
                    } else if (contentLength >= 0 && totalRead >= bodyStart + contentLength) {
                        break;
                    } else if (contentLength == -1) {
                        // No Content-Length and not chunked — read what we can
                        if (totalRead > bodyStart + 100) break;
                    }
                }
            } else {
                Thread.sleep(10);
            }
        }
        socket.close();
        byte[] all = bos.toByteArray();
        String headerStr = new String(all, 0, Math.min(all.length, 8192), "ISO-8859-1");
        int headerEnd = headerStr.indexOf("\r\n\r\n");
        String headers = headerStr.substring(0, headerEnd);
        byte[] body = new byte[all.length - headerEnd - 4];
        System.arraycopy(all, headerEnd + 4, body, 0, body.length);
        return new RawResponse(headers, body);
    }

    private static class RawResponse {
        final String headers;
        final byte[] body;
        RawResponse(String headers, byte[] body) { this.headers = headers; this.body = body; }
    }

    private static String extractHeaderFromRaw(RawResponse resp, String headerName) {
        for (String line : resp.headers.split("\r\n")) {
            if (line.toLowerCase().startsWith(headerName.toLowerCase() + ":")) {
                return line.substring(headerName.length() + 1).trim();
            }
        }
        return null;
    }

    private static String extractBody(String response) {
        int headerEnd = response.indexOf("\r\n\r\n");
        if (headerEnd == -1) return "";
        return response.substring(headerEnd + 4);
    }

    private static String extractHeader(String response, String headerName) {
        int headerEnd = response.indexOf("\r\n\r\n");
        if (headerEnd == -1) return null;
        String headers = response.substring(0, headerEnd);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith(headerName.toLowerCase() + ":")) {
                return line.substring(headerName.length() + 1).trim();
            }
        }
        return null;
    }

    private static byte[] decompressGzip(byte[] compressed) throws Exception {
        GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read;
        while ((read = gzis.read(buf)) != -1) {
            bos.write(buf, 0, read);
        }
        gzis.close();
        return bos.toByteArray();
    }

    // --- Test Cases ---

    public void missingHostHeader_returns400() {
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act — send HTTP/1.1 request without Host header
            String response = sendRequest(port,
                "GET /hello HTTP/1.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("400 Bad Request"),
                "Missing Host header should return 400, got: " +
                    response.substring(0, Math.min(response.length(), 200)));

            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("missingHostHeader_returns400 failed", e);
        }
    }

    public void gzipCompression_compressesResponseBody() {
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act — request gzip compression, read as raw bytes
            RawResponse resp = sendRequestRaw(port,
                "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(resp.headers.contains("200 OK"),
                "Should return 200 OK, got: " +
                    resp.headers.substring(0, Math.min(resp.headers.length(), 100)));
            String encoding = extractHeaderFromRaw(resp, "Content-Encoding");
            TestSupport.assertTrue("gzip".equals(encoding),
                "Content-Encoding should be gzip, got: " + encoding);

            // Verify body is actually compressed (should be smaller than original)
            byte[] decompressed = decompressGzip(resp.body);
            String original = new String(decompressed, "UTF-8");
            TestSupport.assertTrue(original.contains("Hello World!"),
                "Decompressed body should contain original text");
            TestSupport.assertTrue(resp.body.length < decompressed.length,
                "Compressed body should be smaller than original (" +
                    resp.body.length + " vs " + decompressed.length + ")");

            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("gzipCompression_compressesResponseBody failed", e);
        }
    }

    public void noAcceptEncoding_returnsUncompressed() {
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act — request without Accept-Encoding
            String response = sendRequest(port,
                "GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                "Should return 200 OK");
            String encoding = extractHeader(response, "Content-Encoding");
            TestSupport.assertTrue(encoding == null,
                "Content-Encoding should not be present, got: " + encoding);
            String body = extractBody(response);
            TestSupport.assertTrue(body.contains("Hello World!"),
                "Body should contain original text");

            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("noAcceptEncoding_returnsUncompressed failed", e);
        }
    }

    public void smallResponse_skipsGzip() {
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act — request gzip for a tiny response
            String response = sendRequest(port,
                "GET /tiny HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");

            // Assert — small responses (< 1KB) should not be compressed
            TestSupport.assertTrue(response.contains("200 OK"),
                "Should return 200 OK");
            String encoding = extractHeader(response, "Content-Encoding");
            TestSupport.assertTrue(encoding == null,
                "Small response should not be compressed, got Content-Encoding: " + encoding);

            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("smallResponse_skipsGzip failed", e);
        }
    }

    public void chunkedEncoding_producesValidChunkedResponse() {
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            int port = server.getPort();

            // Act
            String response = sendRequest(port,
                "GET /chunked HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                "Should return 200 OK, got: " +
                    response.substring(0, Math.min(response.length(), 100)));
            String transferEncoding = extractHeader(response, "Transfer-Encoding");
            TestSupport.assertTrue("chunked".equals(transferEncoding),
                "Transfer-Encoding should be chunked, got: " + transferEncoding);
            String contentLengthHeader = extractHeader(response, "Content-Length");
            TestSupport.assertTrue(contentLengthHeader == null,
                "Content-Length should not be present in chunked response");
            TestSupport.assertTrue(response.contains("0\r\n\r\n"),
                "Response should end with terminating chunk (0\\r\\n\\r\\n)");
            TestSupport.assertTrue(response.contains("Chunk 0 data"),
                "Response body should contain chunked data");

            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("chunkedEncoding_producesValidChunkedResponse failed", e);
        }
    }
}
