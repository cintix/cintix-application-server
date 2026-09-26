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
}
