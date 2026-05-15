package dk.cintix.application.server.modules.http.server;

import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpConnectionEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpNotificationEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpRequestEvents;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestClient;
import java.net.InetSocketAddress;

/**
 * Public contract for the HTTP server module.
 *
 * @author cix
 */
public interface HttpModule {
    void bind(InetSocketAddress address) throws Exception;
    void bind(InetSocketAddress address, int backlog) throws Exception;
    void addEndpoint(String path, Object endpoint);
    void addEndpoint(String path, Object... endpoints);
    void addWebSocket(String path, Object handler);
    void addGraphQLEndpoint(String path, Object service);
    void addGraphQLEndpoint(String path, Object... services);
    boolean startServer() throws Exception;
    void setDocumentRoot(String documentRoot);
    String getDocumentRoot();
    void setConnectionEvents(HttpConnectionEvents connectionEvents);
    void setRequestEvents(HttpRequestEvents requestEvents);
    void setNotificationEvents(HttpNotificationEvents notificationEvents);
    void setTagsNamespace(String name);
    void addTagClass(String name, Class<?> cls);
    boolean isRunning();
    void setRunning(boolean running);
    void connectedEvent(RestClient client);
    void disconnectedEvent(RestClient client);
    void requestEvent(RestClient client, RestHttpRequest request);
    void notifyEvent(String msg);
}
