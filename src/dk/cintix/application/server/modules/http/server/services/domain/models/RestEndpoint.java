package dk.cintix.application.server.modules.http.server.services.domain.models;

import dk.cintix.application.server.infrastructure.annotations.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author cix
 */
public class RestEndpoint {

    private static final Logger logger = Logger.getLogger(RestEndpoint.class.getName());
    private final String path;
    private final Method method;
    private final Object object;

    public RestEndpoint(String path, Method method, Object object) {
        this.path = path;
        this.method = method;
        this.object = object;
    }

    public String getPath() {
        return path;
    }

    public Method getMethod() {
        return method;
    }

    public Object getObject() {
        return object;
    }

    public void addInjection(Object obj) {
        try {
            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Inject.class)) {
                    if (field.getType() == obj.getClass()) {
                        field.setAccessible(true);
                        field.set(object, obj);
                    }
                }
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to inject dependency into endpoint: " + path, exception);
        }
    }

    @Override
    public String toString() {
        return "RestEndpoint {" + "path=" + path + ", method=" + method + ", object=" + object + '}';
    }

}
