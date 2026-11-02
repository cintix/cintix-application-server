package dk.cintix.application.server.rest.http;

import dk.cintix.application.server.TestSupport;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Proves that the static-file jail cannot be escaped.
 *
 * <p>The old check compared {@code File.getAbsolutePath()} prefixes, which
 * neither normalises {@code ..} nor distinguishes a sibling directory sharing
 * the root's name prefix — so {@code /../outside.txt} and
 * {@code /../web-evil/secret.txt} were served from outside {@code DOCUMENT_ROOT}.
 * The check now compares canonical paths with a separator boundary.</p>
 */
public class RestHttpServerDocumentJailTest {

    public void runAll() {
        documentJail_blocksTraversalSiblingPrefixAndSymlinkEscape();
    }

    public void documentJail_blocksTraversalSiblingPrefixAndSymlinkEscape() {
        Path tempRoot = null;
        RestHttpServer server = null;
        Thread serverThread = null;
        try {
            // Arrange — a document root with a sibling file, a sibling
            // directory whose name starts with the root's name, and a symlink
            // inside the root pointing out of it.
            tempRoot = Files.createTempDirectory("cas-document-jail");
            Path documentRoot = Files.createDirectory(tempRoot.resolve("web"));
            Files.write(documentRoot.resolve("inside.txt"),
                    "INSIDE".getBytes(StandardCharsets.UTF_8));

            Path outside = tempRoot.resolve("outside.txt");
            Files.write(outside, "OUTSIDE-SECRET".getBytes(StandardCharsets.UTF_8));

            Path prefixSibling = Files.createDirectory(tempRoot.resolve("web-evil"));
            Files.write(prefixSibling.resolve("secret.txt"),
                    "PREFIX-SECRET".getBytes(StandardCharsets.UTF_8));

            Path symlink = documentRoot.resolve("link.txt");
            boolean symlinkCreated = false;
            try {
                Files.createSymbolicLink(symlink, outside);
                symlinkCreated = true;
            } catch (Exception e) {
                // Some filesystems forbid symlinks; the other vectors still run.
            }

            server = new RestHttpServer() {};
            server.setDocumentRoot(documentRoot.toAbsolutePath().toString());
            server.bind(new InetSocketAddress(0));

            final RestHttpServer runningServer = server;
            serverThread = new Thread(() -> {
                try {
                    runningServer.startServer();
                } catch (Exception e) {
                    // Server stopped
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            Thread.sleep(100);
            int port = server.getPort();

            // Act / Assert — a file inside the root is still served
            String inside = sendRequest(port, "GET /inside.txt HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
            TestSupport.assertTrue(inside.contains("200 OK") && inside.contains("INSIDE"),
                    "Files inside DOCUMENT_ROOT must still be served. Response: " + inside);

            // Act / Assert — ".." must not reach a sibling of the root
            String traversal = sendRequest(port, "GET /../outside.txt HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
            TestSupport.assertFalse(traversal.contains("OUTSIDE-SECRET"),
                    "'..' traversal escaped the document root. Response: " + traversal);
            TestSupport.assertTrue(traversal.contains("404"),
                    "Traversal must be answered with 404. Response: " + traversal);

            // Act / Assert — a sibling directory sharing the root's prefix must
            // not satisfy the jail check
            String prefix = sendRequest(port, "GET /../web-evil/secret.txt HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
            TestSupport.assertFalse(prefix.contains("PREFIX-SECRET"),
                    "Sibling directory with the same name prefix escaped the jail. Response: " + prefix);
            TestSupport.assertTrue(prefix.contains("404"),
                    "Prefix-sibling access must be answered with 404. Response: " + prefix);

            // Act / Assert — a symlink inside the root must not leak its target
            if (symlinkCreated) {
                String linked = sendRequest(port, "GET /link.txt HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
                TestSupport.assertFalse(linked.contains("OUTSIDE-SECRET"),
                        "Symlink escaped the document root. Response: " + linked);
                TestSupport.assertTrue(linked.contains("404"),
                        "Symlink escape must be answered with 404. Response: " + linked);
            }
        } catch (Exception e) {
            throw new RuntimeException("documentJail_blocksTraversalSiblingPrefixAndSymlinkEscape failed", e);
        } finally {
            if (server != null) {
                server.setRunning(false);
            }
            if (serverThread != null) {
                try {
                    serverThread.join(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            deleteRecursively(tempRoot);
        }
    }

    private static String sendRequest(int port, String httpRequest) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(httpRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();

            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            long start = System.currentTimeMillis();
            while ((System.currentTimeMillis() - start) < 3000) {
                int available = in.available();
                if (available > 0) {
                    int read = in.read(buf, 0, Math.min(available, buf.length));
                    if (read == -1) {
                        break;
                    }
                    sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
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
        } finally {
            socket.close();
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort cleanup
                        }
                    });
        } catch (Exception ignored) {
            // Best effort cleanup
        }
    }
}
