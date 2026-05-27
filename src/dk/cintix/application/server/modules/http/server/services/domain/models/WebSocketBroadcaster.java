package dk.cintix.application.server.modules.http.server.services.domain.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans out messages to all WebSocket sessions on a given path.
 *
 * @author cix
 */
public class WebSocketBroadcaster {

    private final Map<String, List<WebSocketSession>> sessionsByPath = new LinkedHashMap<>();

    public void register(String path, WebSocketSession session) {
        synchronized (sessionsByPath) {
            sessionsByPath.computeIfAbsent(path, k -> new ArrayList<>()).add(session);
        }
    }

    public void unregister(String path, WebSocketSession session) {
        synchronized (sessionsByPath) {
            List<WebSocketSession> sessions = sessionsByPath.get(path);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByPath.remove(path);
                }
            }
        }
    }

    public void broadcast(String path, String message) {
        List<WebSocketSession> sessions;
        synchronized (sessionsByPath) {
            sessions = new ArrayList<>(sessionsByPath.getOrDefault(path, new ArrayList<>()));
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.send(message);
                } catch (Exception e) {
                    // Stale session — unregister so it doesn't break future broadcasts
                    unregister(path, session);
                }
            }
        }
    }

    public void broadcast(String path, byte[] data) {
        List<WebSocketSession> sessions;
        synchronized (sessionsByPath) {
            sessions = new ArrayList<>(sessionsByPath.getOrDefault(path, new ArrayList<>()));
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.send(data);
                } catch (Exception e) {
                    // Stale session — unregister so it doesn't break future broadcasts
                    unregister(path, session);
                }
            }
        }
    }

    public int getConnectionCount(String path) {
        synchronized (sessionsByPath) {
            List<WebSocketSession> sessions = sessionsByPath.get(path);
            return sessions != null ? sessions.size() : 0;
        }
    }
}
