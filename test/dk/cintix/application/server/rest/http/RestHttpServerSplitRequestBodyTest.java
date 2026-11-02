package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.infrastructure.annotations.Action;
import dk.cintix.application.server.infrastructure.annotations.Inject;
import dk.cintix.application.server.infrastructure.annotations.POST;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Proves over a real socket that a request split across TCP segments is
 * assembled rather than parsed truncated.
 *
 * <p>The echo endpoint reports the byte length of the body it received, so the
 * assertion is on the payload itself, not on a status code that could succeed
 * with a short body.</p>
 */
public class RestHttpServerSplitRequestBodyTest {

    public void runAll() {
        splitRequest_headersThenBody_bodyArrivesWhole();
        splitRequest_bodyLargerThanReadBuffer_arrivesWhole();
        splitRequest_headersSplitAcrossSegments_stillParsed();
        multiValueQueryAndForm_reachRequestAccessors();
        jsonBody_isNotParsedAsForm();
        requestLargerThanConfiguredLimit_returns413();
        chunkedRequestBody_reachesEndpointDecoded();
        chunkedRequestBody_splitAcrossSegments_stillAssembled();
        chunkedRequestBody_invalidChunkSize_returns400();
        expectContinue_clientGets100BeforeUploading();
    }

    /** Wraps {@code payload} in chunked transfer coding with fixed-size chunks. */
    private static String chunkPayload(String payload, int chunkSize) {
        StringBuilder encoded = new StringBuilder();
        for (int offset = 0; offset < payload.length(); offset += chunkSize) {
            String chunk = payload.substring(offset, Math.min(offset + chunkSize, payload.length()));
            encoded.append(Integer.toHexString(chunk.length())).append("\r\n")
                   .append(chunk).append("\r\n");
        }
        encoded.append("0\r\n\r\n");
        return encoded.toString();
    }

    public static class EchoEndpoint {

        // Form fields are only reachable through the request object, and
        // @Inject is the one supported injection path for RestHttpRequest.
        @Inject
        RestHttpRequest injectedRequest;

        @POST
        @Action(path = "/echo")
        public Response echo(String body) {
            return new Response().OK().ContentType("text/plain")
                    .data("len=" + (body == null ? -1 : body.length()));
        }

        @POST
        @Action(path = "/form")
        public Response form(String body) {
            Map<String, String> fields = injectedRequest.getPostParams();
            return new Response().OK().ContentType("text/plain")
                    .data("name=" + fields.get("name") + ";mode=" + fields.get("mode"));
        }

        @POST
        @Action(path = "/multi")
        public Response multi(String body) {
            return new Response().OK().ContentType("text/plain")
                    .data("tags=" + injectedRequest.getQueryStringValues("tag")
                            + ";xs=" + injectedRequest.getPostValues("x"));
        }

        @POST
        @Action(path = "/jsonform")
        public Response jsonForm(String body) {
            return new Response().OK().ContentType("text/plain")
                    .data("hasA=" + injectedRequest.getPostParams().containsKey("a")
                            + ";raw=" + body);
        }
    }

    private static RestHttpServer createServer() throws Exception {
        RestHttpServer server = new RestHttpServer() {};
        server.addEndpoint("", new EchoEndpoint());
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
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        return serverThread;
    }

    private static String readResponse(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 5000) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buf, 0, Math.min(available, buf.length));
                if (read == -1) {
                    break;
                }
                sb.append(new String(buf, 0, read));
                String sofar = sb.toString();
                int headerEnd = sofar.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    int contentLength = 0;
                    for (String line : sofar.substring(0, headerEnd).split("\r\n")) {
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
        return sb.toString();
    }

    public void splitRequest_headersThenBody_bodyArrivesWhole() {
        // Arrange
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "name=alice&mode=on";

            // Act — headers and body in two separate writes, with a pause so
            // they cannot be coalesced into one segment by the network stack
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n").getBytes());
            out.flush();
            Thread.sleep(300);
            out.write(body.getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — the handler saw the whole body, not an empty one
            TestSupport.assertTrue(response.contains("200 OK"),
                    "Expected 200 OK, got: " + response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("len=" + body.length()),
                    "Body was truncated across segments. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("splitRequest_headersThenBody_bodyArrivesWhole failed", e);
        }
    }

    public void splitRequest_bodyLargerThanReadBuffer_arrivesWhole() {
        // Arrange — the read buffer is 2 KB, so this body needs many reads
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            StringBuilder builder = new StringBuilder();
            while (builder.length() < 20000) {
                builder.append("0123456789");
            }
            String body = builder.toString();

            // Act
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n").getBytes());
            out.flush();
            Thread.sleep(300);
            out.write(body.getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — the whole 20 KB body reached the endpoint
            TestSupport.assertTrue(response.contains("len=" + body.length()),
                    "Large body truncated. Expected len=" + body.length() + " in: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("splitRequest_bodyLargerThanReadBuffer_arrivesWhole failed", e);
        }
    }

    public void splitRequest_headersSplitAcrossSegments_stillParsed() {
        // Arrange — headers themselves arrive in pieces
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "name=bob&mode=off";

            // Act
            out.write("POST /form HTTP/1.1\r\nHost: 127.0.0.1\r\n".getBytes());
            out.flush();
            Thread.sleep(200);
            out.write(("Content-Type: application/x-www-form-urlencoded\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n").getBytes());
            out.flush();
            Thread.sleep(200);
            out.write(body.getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — form fields parsed from the reassembled request
            TestSupport.assertTrue(response.contains("200 OK"),
                    "Expected 200 OK, got: " + response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("name=bob") && response.contains("mode=off"),
                    "Form fields lost when headers were split. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("splitRequest_headersSplitAcrossSegments_stillParsed failed", e);
        }
    }

    public void multiValueQueryAndForm_reachRequestAccessors() {
        // Arrange — repeated query parameter and repeated form field
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "x=1&x=2";

            // Act
            out.write(("POST /multi?tag=a&tag=b HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/x-www-form-urlencoded\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body).getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — both values survived the round trip, in order
            TestSupport.assertTrue(response.contains("tags=[a, b]"),
                    "Repeated query values lost. Response: " + response);
            TestSupport.assertTrue(response.contains("xs=[1, 2]"),
                    "Repeated form values lost. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("multiValueQueryAndForm_reachRequestAccessors failed", e);
        }
    }

    public void jsonBody_isNotParsedAsForm() {
        // Arrange — a JSON body containing '=' must not produce a form field
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "{\"a\":\"x=1\"}";

            // Act
            out.write(("POST /jsonform HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body).getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — no bogus field, but the raw body still reaches the handler
            TestSupport.assertTrue(response.contains("hasA=false"),
                    "JSON body was mined for form fields. Response: " + response);
            TestSupport.assertTrue(response.contains("raw={\"a\":\"x=1\"}"),
                    "Raw JSON body did not reach the handler. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("jsonBody_isNotParsedAsForm failed", e);
        }
    }

    public void requestLargerThanConfiguredLimit_returns413() {
        // Arrange — lower the limit well below the default 5 MB
        try {
            RestHttpServer server = createServer();
            server.setMaxRequestBytes(1024);
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            StringBuilder builder = new StringBuilder();
            while (builder.length() < 2000) {
                builder.append("0123456789");
            }
            String body = builder.toString();

            // Act
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body).getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — refused, not truncated and handed to the endpoint
            TestSupport.assertTrue(response.contains("413"),
                    "Expected 413 for a request above the configured limit. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("requestLargerThanConfiguredLimit_returns413 failed", e);
        }
    }

    public void chunkedRequestBody_reachesEndpointDecoded() {
        // Arrange — before chunk support the endpoint received an empty body
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "name=alice&mode=on";
            String encoded = chunkPayload(body, 5);

            // Act — the wire body is longer than the payload, so a failure to
            // de-chunk shows up as a wrong length, not just a wrong status
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "\r\n"
                    + encoded).getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert
            TestSupport.assertTrue(response.contains("200 OK"),
                    "Expected 200 OK, got: " + response.substring(0, Math.min(response.length(), 200)));
            TestSupport.assertTrue(response.contains("len=" + body.length()),
                    "Chunked body was not de-chunked before dispatch. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("chunkedRequestBody_reachesEndpointDecoded failed", e);
        }
    }

    public void chunkedRequestBody_splitAcrossSegments_stillAssembled() {
        // Arrange — chunks arrive in separate TCP segments
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "abcdefghijklmnopqrstuvwxyz";

            // Act
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "\r\n").getBytes());
            out.flush();
            Thread.sleep(200);
            out.write("a\r\nabcdefghij\r\n".getBytes());
            out.flush();
            Thread.sleep(200);
            out.write("a\r\nklmnopqrst\r\n".getBytes());
            out.flush();
            Thread.sleep(200);
            out.write("6\r\nuvwxyz\r\n0\r\n\r\n".getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert
            TestSupport.assertTrue(response.contains("len=" + body.length()),
                    "Chunked body split across segments was lost. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("chunkedRequestBody_splitAcrossSegments_stillAssembled failed", e);
        }
    }

    public void chunkedRequestBody_invalidChunkSize_returns400() {
        // Arrange — "zz" is not a hexadecimal chunk size
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Act
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "\r\n"
                    + "zz\r\nabc\r\n0\r\n\r\n").getBytes());
            out.flush();

            String response = readResponse(in);

            // Assert — refused, not silently handed an empty body
            TestSupport.assertTrue(response.contains("400 Bad Request"),
                    "Expected 400 for malformed chunk framing. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("chunkedRequestBody_invalidChunkSize_returns400 failed", e);
        }
    }

    public void expectContinue_clientGets100BeforeUploading() {
        // Arrange — the client holds the body back until it hears "100"
        try {
            RestHttpServer server = createServer();
            Thread serverThread = startServer(server);
            Socket socket = new Socket("127.0.0.1", server.getPort());
            socket.setSoTimeout(4000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String body = "name=alice";

            // Act — headers only, then wait for the interim response
            out.write(("POST /echo HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Expect: 100-continue\r\n"
                    + "\r\n").getBytes());
            out.flush();

            String interim = readResponse(in);

            // Assert — the interim response arrives before the body is sent
            TestSupport.assertTrue(interim.contains("100 Continue"),
                    "Client was left waiting instead of being told to continue. Got: " + interim);

            // Act — the body follows the interim response
            out.write(body.getBytes());
            out.flush();
            String response = readResponse(in);

            // Assert — and the request then runs normally
            TestSupport.assertTrue(response.contains("200 OK"),
                    "Expected 200 OK after continuing, got: " + response);
            TestSupport.assertTrue(response.contains("len=" + body.length()),
                    "Body lost after the interim response. Response: " + response);

            socket.close();
            server.setRunning(false);
            serverThread.join(2000);
        } catch (Exception e) {
            throw new RuntimeException("expectContinue_clientGets100BeforeUploading failed", e);
        }
    }
}
