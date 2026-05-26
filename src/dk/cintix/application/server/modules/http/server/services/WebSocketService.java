package dk.cintix.application.server.modules.http.server.services;

import dk.cintix.application.server.infrastructure.annotations.OnBinary;
import dk.cintix.application.server.infrastructure.annotations.OnClose;
import dk.cintix.application.server.infrastructure.annotations.OnError;
import dk.cintix.application.server.infrastructure.annotations.OnMessage;
import dk.cintix.application.server.infrastructure.annotations.OnOpen;
import dk.cintix.application.server.infrastructure.annotations.WebSocket;
import dk.cintix.application.server.modules.http.server.endpoint.InternalClientSession;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.WebSocketFrame;
import dk.cintix.application.server.modules.http.server.endpoint.WebSocketSessionImpl;
import dk.cintix.application.server.modules.http.server.services.domain.models.WebSocketBroadcaster;
import dk.cintix.application.server.modules.http.server.services.domain.models.WebSocketSession;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles WebSocket upgrade, frame dispatch, and lifecycle.
 *
 * @author cix
 */
public class WebSocketService {

    private final Map<String, HandlerEntry> handlers = new LinkedHashMap<>();
    private final WebSocketBroadcaster broadcaster = new WebSocketBroadcaster();

    public WebSocketBroadcaster getBroadcaster() {
        return broadcaster;
    }

    public boolean hasHandler(String path) {
        return handlers.containsKey(path);
    }

    public void register(String path, Object handler) {
        HandlerEntry entry = new HandlerEntry();
        entry.handler = handler;
        entry.path = path;

        for (Method method : handler.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            if (method.isAnnotationPresent(OnOpen.class)) {
                entry.onOpen = method;
            } else if (method.isAnnotationPresent(OnMessage.class)) {
                entry.onMessage = method;
            } else if (method.isAnnotationPresent(OnBinary.class)) {
                entry.onBinary = method;
            } else if (method.isAnnotationPresent(OnClose.class)) {
                entry.onClose = method;
            } else if (method.isAnnotationPresent(OnError.class)) {
                entry.onError = method;
            }
        }

        handlers.put(path, entry);
    }

    public boolean isWebSocketUpgrade(RestHttpRequest request) {
        return "websocket".equalsIgnoreCase(request.getHeader("UPGRADE"))
                && "upgrade".equalsIgnoreCase(request.getHeader("CONNECTION"))
                && request.getHeader("SEC-WEBSOCKET-KEY") != null;
    }

    public void handleUpgrade(RestHttpRequest request, SocketChannel channel, SelectionKey key,
            InternalClientSession clientSession) throws IOException {
        String path = request.getContextPath();
        if (path.isEmpty()) {
            path = "/";
        }

        HandlerEntry entry = handlers.get(path);
        if (entry == null) {
            String response = "HTTP/1.1 404 Not Found\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: Close\r\n\r\n";
            channel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            return;
        }

        String clientKey = request.getHeader("SEC-WEBSOCKET-KEY");
        String acceptKey = WebSocketFrame.computeAcceptKey(clientKey);

        String upgradeResponse = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        channel.write(ByteBuffer.wrap(upgradeResponse.getBytes(StandardCharsets.UTF_8)));

        WebSocketSession session = new WebSocketSession(clientSession.getSessionId(),
                channel.socket().getRemoteSocketAddress().toString());
        WebSocketSessionImpl impl = new WebSocketSessionImpl(channel, key, session);

        clientSession.add("ws-mode", Boolean.TRUE);
        clientSession.add("ws-session", session);
        clientSession.add("ws-impl", impl);
        clientSession.add("ws-path", path);
        clientSession.add("ws-buffer", ByteBuffer.allocate(8192));

        for (Map.Entry<String, String> qs : request.getQueryStrings().entrySet()) {
            session.addAttribute("qs." + qs.getKey(), qs.getValue());
        }

        broadcaster.register(path, session);

        key.interestOps(SelectionKey.OP_READ);

        invokeMethod(entry.onOpen, entry.handler, session);
    }

    public void handleFrame(byte[] rawBytes, InternalClientSession clientSession,
            SelectionKey key, SocketChannel channel) {
        WebSocketSession session = (WebSocketSession) clientSession.get("ws-session");
        if (session == null) {
            return;
        }

        String path = (String) clientSession.get("ws-path");
        HandlerEntry entry = handlers.get(path);
        if (entry == null) {
            return;
        }

        ByteBuffer buffer = (ByteBuffer) clientSession.get("ws-buffer");
        if (buffer == null) {
            buffer = ByteBuffer.allocate(8192);
            clientSession.add("ws-buffer", buffer);
        }

        buffer.put(rawBytes);
        List<WebSocketFrame.Frame> frames = WebSocketFrame.parseFrames(buffer);

        for (WebSocketFrame.Frame frame : frames) {
            try {
                switch (frame.opcode) {
                    case WebSocketFrame.OP_TEXT:
                        String text = new String(frame.payload, StandardCharsets.UTF_8);
                        invokeMethod(entry.onMessage, entry.handler, session, text);
                        break;
                    case WebSocketFrame.OP_BINARY:
                        invokeMethod(entry.onBinary, entry.handler, session, frame.payload);
                        break;
                    case WebSocketFrame.OP_PING:
                        channel.write(ByteBuffer.wrap(WebSocketFrame.encodePong(frame.payload)));
                        break;
                    case WebSocketFrame.OP_PONG:
                        break;
                    case WebSocketFrame.OP_CLOSE:
                        int statusCode = 1000;
                        String reason = "";
                        if (frame.payload.length >= 2) {
                            statusCode = ((frame.payload[0] & 0xFF) << 8) | (frame.payload[1] & 0xFF);
                            if (frame.payload.length > 2) {
                                reason = new String(frame.payload, 2, frame.payload.length - 2, StandardCharsets.UTF_8);
                            }
                        }
                        WebSocketSessionImpl impl = (WebSocketSessionImpl) clientSession.get("ws-impl");
                        if (impl != null && !impl.isCloseFrameSent()) {
                            channel.write(ByteBuffer.wrap(WebSocketFrame.encodeClose(statusCode, "")));
                        }
                        session.setOpen(false);
                        invokeMethod(entry.onClose, entry.handler, session, statusCode, reason);
                        broadcaster.unregister(path, session);
                        key.cancel();
                        channel.close();
                        break;
                }
            } catch (Exception e) {
                invokeMethod(entry.onError, entry.handler, session, e);
            }
        }
    }

    public void handleWrite(InternalClientSession clientSession, SocketChannel channel) throws IOException {
        WebSocketSessionImpl impl = (WebSocketSessionImpl) clientSession.get("ws-impl");
        if (impl == null) {
            return;
        }
        impl.writePending();
    }

    public void handleDisconnect(InternalClientSession clientSession) {
        WebSocketSession session = (WebSocketSession) clientSession.get("ws-session");
        String path = (String) clientSession.get("ws-path");
        if (session != null && path != null) {
            session.setOpen(false);
            broadcaster.unregister(path, session);
        }
    }

    private void invokeMethod(Method method, Object handler, WebSocketSession session, Object... extraArgs) {
        if (method == null) {
            return;
        }
        try {
            int paramCount = method.getParameterCount();
            Object[] args = new Object[paramCount];
            if (paramCount > 0) {
                args[0] = session;
            }
            if (extraArgs != null) {
                int copyCount = Math.min(extraArgs.length, paramCount - 1);
                System.arraycopy(extraArgs, 0, args, 1, copyCount);
            }
            method.invoke(handler, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static class HandlerEntry {
        String path;
        Object handler;
        Method onOpen;
        Method onMessage;
        Method onBinary;
        Method onClose;
        Method onError;
    }
}
