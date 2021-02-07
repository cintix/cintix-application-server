/*
 */
package dk.cintix.tinyserver.rest;

import dk.cintix.tinyserver.rest.annotations.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 *
 * @author cix
 */
public class RestEndpoint {

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
            exception.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "RestEndpoint {" + "path=" + path + ", method=" + method + ", object=" + object + '}';
    }

}
