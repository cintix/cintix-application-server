package dk.cintix.application.server.modules.http.server;

import dk.cintix.application.server.infrastructure.ReflectionUtil;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpConnectionEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpNotificationEvents;
import dk.cintix.application.server.modules.http.server.endpoint.events.HttpRequestEvents;
import dk.cintix.application.server.modules.http.server.services.domain.models.Response;
import dk.cintix.application.server.modules.http.server.services.domain.models.RestClient;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;

/**
 * Public contract for the HTTP server module.
 *
 * @author cix
 */
public interface HttpModule {
    public static final class EndpointInfo {
        private final String path;
        private final Method method;
        private final Object handler;

        public EndpointInfo(String path, Method method, Object handler) {
            this.path = path;
            this.method = method;
            this.handler = handler;
        }

        public String getPath() {
            return path;
        }

        public Method getMethod() {
            return ReflectionUtil.getBestDescribedMethod(method, handler);
        }

        public Object getHandler() {
            return handler;
        }

        public Class<?> getHandlerType() {
            return handler.getClass();
        }

        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            Method describedMethod = getMethod();
            if (describedMethod.isAnnotationPresent(annotationType)) {
                return describedMethod.getAnnotation(annotationType);
            }
            return handler.getClass().getAnnotation(annotationType);
        }
    }

    public static interface RequestFilter {
        Response filter(RestHttpRequest request, EndpointInfo endpoint);
    }

    void bind(InetSocketAddress address) throws Exception;
    void bind(InetSocketAddress address, int backlog) throws Exception;
    void addEndpoint(String path, Object endpoint);
    void addEndpoint(String path, Object... endpoints);
    void addWebSocket(String path, Object handler);
    void addRequestFilter(RequestFilter filter);
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
