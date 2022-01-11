/*
 */
package dk.cintix.application.server.rest.jsd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dk.cintix.application.server.io.ReflectionUtil;
import dk.cintix.application.server.rest.annotations.Action;
import dk.cintix.application.server.rest.annotations.CacheByStatus;
import dk.cintix.application.server.rest.annotations.DELETE;
import dk.cintix.application.server.rest.annotations.POST;
import dk.cintix.application.server.rest.annotations.PUT;
import dk.cintix.application.server.rest.annotations.Static;
import dk.cintix.application.server.rest.http.Status;
import dk.cintix.application.server.rest.jsd.models.ArgumentDefinition;
import dk.cintix.application.server.rest.jsd.models.Cache;
import dk.cintix.application.server.rest.jsd.models.ModelDefinition;
import dk.cintix.application.server.rest.jsd.models.Service;
import dk.cintix.application.server.rest.jsd.models.ServiceAction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 *
 * @author cix
 */
public class JsonServiceDescriptionEngine {

    /**
     *
     * @param uri
     * @param name
     * @param endpointObject
     * @return
     */
    public static Service generateServiceDefination(String uri, String name, Object endpointObject) {
        Class endpoint = endpointObject.getClass();

        Service service = new Service();
        service.setName((name != null) ? name : endpoint.getSimpleName());
        service.setUri(uri);

        Method[] methods = endpoint.getMethods();
        for (Method methodOrginal : methods) {
            Method method = ReflectionUtil.getBestDescribedMethod(methodOrginal, endpointObject);
            if (method.getAnnotation(Action.class) != null) {
                Action action = method.getAnnotation(Action.class);

                ServiceAction serviceAction = new ServiceAction();
                serviceAction.setUri(action.path());
                serviceAction.setAction(ServiceAction.ActionType.GET);
                serviceAction.setAccepts(action.consume());

                if (method.getAnnotation(PUT.class) != null) {
                    serviceAction.setAction(ServiceAction.ActionType.PUT);
                } else if (method.getAnnotation(POST.class) != null) {
                    serviceAction.setAction(ServiceAction.ActionType.POST);
                } else if (method.getAnnotation(DELETE.class) != null) {
                    serviceAction.setAction(ServiceAction.ActionType.DELETE);
                }

                for (Parameter parameter : method.getParameters()) {
                    ArgumentDefinition definition = getArgumentDefinition(parameter.getName(), parameter.getType());
                    serviceAction.addArgument(definition);
                }

                if (method.getAnnotation(Static.class) != null) {
                    Cache cache = new Cache();
                    cache.setDescription("Static");
                    cache.setTimeToLive(-1);
                    cache.setStatus(Status.All);
                    serviceAction.addCache(cache);
                }

                if (method.getAnnotation(CacheByStatus.class) != null) {
                    dk.cintix.application.server.rest.annotations.Cache[] caches = method.getAnnotation(CacheByStatus.class).value();
                    for (dk.cintix.application.server.rest.annotations.Cache cache : caches) {
                        for (Status stat : cache.status()) {
                            Cache c = new Cache();
                            c.setDescription("Cache for " + ((stat == Status.All) ? " all statuses" : stat.name()));
                            c.setTimeToLive(cache.timeToLive());
                            c.setStatus(stat);
                            serviceAction.addCache(c);
                        }

                    }
                }

                service.addMethod(serviceAction);
            }

        }

        return service;
    }

    /**
     *
     * @param obj
     * @return
     */
    private static ArgumentDefinition getArgumentDefinition(String name, Class obj) {
        ArgumentDefinition definition = new ArgumentDefinition();
        definition.setName(name);

        switch (obj.getTypeName()) {
            case "int":
                definition.setType("integer");
                return definition;
            case "boolean":
            case "short":
            case "long":
            case "double":
            case "float":
            case "byte":
                definition.setType(obj.getTypeName());
                return definition;
        }

        if (obj.getTypeName().startsWith("java.lang.")) {
            definition.setType(obj.getSimpleName().toLowerCase());
            return definition;
        }

        try {
            Field[] declaredFields = obj.getDeclaredFields();
            definition.setType("class");
            ModelDefinition modelDefinition = new ModelDefinition();
            for (Field field : declaredFields) {
                modelDefinition.addDefinition(getArgumentDefinition(field.getName(), field.getType()));
            }

            definition.setModel(modelDefinition);
            return definition;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
