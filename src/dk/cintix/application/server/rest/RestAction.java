/*
 */
package dk.cintix.application.server.rest;

import dk.cintix.application.server.io.ReflectionUtil;
import dk.cintix.application.server.io.cache.CacheType;
import dk.cintix.application.server.model.ModelGenerator;
import dk.cintix.application.server.rest.annotations.Action;
import dk.cintix.application.server.rest.annotations.Cache;
import dk.cintix.application.server.rest.annotations.CacheByStatus;
import dk.cintix.application.server.rest.annotations.Inject;
import dk.cintix.application.server.rest.annotations.Static;
import dk.cintix.application.server.rest.http.Status;
import dk.cintix.application.server.rest.http.request.RestHttpRequest;
import dk.cintix.application.server.rest.http.utils.HttpUtil;
import dk.cintix.application.server.rest.response.CachedResponse;
import dk.cintix.application.server.rest.response.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author cix
 */
public class RestAction {

    private static final Map<String, dk.cintix.application.server.io.cache.Cache> _CACHE_MAPS = new LinkedHashMap<>();
    private final List<String> arguments;
    private final RestEndpoint endpoint;
    private ModelGenerator generator;

    public RestAction(RestEndpoint endpoint, List<String> arguments) {
        this.arguments = arguments;
        this.endpoint = endpoint;
    }

    public void addArgument(String arg) {
        arguments.add(arg);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public RestEndpoint getEndpoint() {
        return endpoint;
    }

    public Response process(RestHttpRequest request) {
        try {

            for (Field field : endpoint.getObject().getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    if (field.getType().equals(RestHttpRequest.class)) {
                        field.setAccessible(true);
                        field.set(endpoint.getObject(), request);
                    }
                }
            }

            Method original = endpoint.getMethod();
            Method method = ReflectionUtil.getBestDescribedMethod(endpoint.getMethod(), endpoint.getObject());
;            
            Parameter[] parameterTypes = method.getParameters();
            Object[] methodArguments = new Object[parameterTypes.length];

            String cacheBaseId = baseId(method.toString());
            String requestId = baseId(Arrays.toString(methodArguments));

            CacheType cacheType = getCacheStategy(method);
            boolean useCache = (cacheType != CacheType.NONE);
            dk.cintix.application.server.io.cache.Cache cache = _CACHE_MAPS.get(cacheBaseId);

            if (useCache && cache != null) {
                if (cache.contains(requestId)) {
                    CachedResponse response = new CachedResponse(cache.get(requestId).toString().getBytes());
                    if (response != null) {
                        return response;
                    }
                }
            }

            String accept = method.getAnnotation(Action.class).consume();

            if (!HttpUtil.contentTypeMatch(accept, request.getContentType())) {
                return new Response().NotFound();
            } else {
                Map<String, ModelGenerator> contextGenerators = Response.getContextGenerators();
                if (contextGenerators.containsKey(accept)) {
                    generator = contextGenerators.get(accept);
                } else {
                    generator = contextGenerators.get("default");
                }
            }
            if (parameterTypes.length == 1 && arguments.isEmpty() && (request.getMethod().toUpperCase().equals("POST") || request.getMethod().toUpperCase().equals("PUT"))) {
                Parameter parameter = parameterTypes[0];
                methodArguments[0] = valueFromType(parameter, request.getRawPost());
            } else {
                for (int index = 0; index < parameterTypes.length; index++) {
                    Parameter parameter = parameterTypes[index];
                    String value = arguments.get(index);
                    methodArguments[index] = valueFromType(parameter, value);
                }
            }

            Response response = (Response) original.invoke(endpoint.getObject(), methodArguments);

            if (useCache) {
                if (cache == null) {
                    CacheByStatus cacheByStatus = method.getAnnotation(CacheByStatus.class);
                    String cachedResponseString = new String(response.build());

                    if (cacheByStatus == null) {
                        cache = new dk.cintix.application.server.io.cache.Cache<String, String>(1);
                    } else {
                        int currentStatusCode = response.getStatus();
                        for (Cache cacheOptions : cacheByStatus.value()) {
                            if (isStatusDefinedInCache(cacheOptions.status(), currentStatusCode)) {
                                cache = new dk.cintix.application.server.io.cache.Cache<String, String>(cacheOptions.timeToLive(), cacheOptions.size());
                                cache.put(requestId, cachedResponseString, cacheType);
                            }
                        }
                    }
                    cache.put(requestId, cachedResponseString, cacheType);
                }
                _CACHE_MAPS.put(cacheBaseId, cache);
            }

            return response;
        } catch (Exception exception) {
            exception.printStackTrace();
            return new Response().InternalServerError().data(exception.toString());
        }
    }

    private boolean isStatusDefinedInCache(Status[] status, int value) {
        for (int i = 0; i < status.length; i++) {
            if (status[i].getValue() == -1) {
                return true;
            }
            if (status[i].getValue() == value) {
                return true;
            }
        }
        return false;
    }

    private String baseId(String name) {
        return new String(Base64.getEncoder().encode(name.getBytes()));
    }

    private Object valueFromType(Parameter parameter, String value) throws Exception {
        Object valueFromType = ReflectionUtil.valueFromType(parameter, value);
        if (valueFromType != null) {
            return valueFromType;
        } else {
            return generator.toModel(value, parameter.getType());
        }
    }

    private CacheType getCacheStategy(Method method) {
        if (method.isAnnotationPresent(Static.class)) {
            return CacheType.STATIC;
        }
        if (method.isAnnotationPresent(Cache.class)) {
            return CacheType.DYNAMIC;
        }
        return CacheType.NONE;
    }

    @Override
    public String toString() {
        return "RestAction{" + "arguments=" + arguments + ", endpoint=" + endpoint + '}';
    }

}
