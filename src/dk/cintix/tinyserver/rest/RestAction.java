/*
 */
package dk.cintix.tinyserver.rest;

import dk.cintix.tinyserver.model.ModelGenerator;
import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.annotations.Inject;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.rest.http.utils.HttpUtil;
import dk.cintix.tinyserver.rest.response.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 *
 * @author cix
 */
public class RestAction {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
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

            Method method = endpoint.getMethod();
            Parameter[] parameterTypes = method.getParameters();
            Object[] methodArguments = new Object[parameterTypes.length];

            String accept = method.getAnnotation(Action.class).consume();

            if (!HttpUtil.contentTypeMatch(accept, request.getContentType())) {
                return new Response().NotFound();
            } else {
                Map<String, ModelGenerator> contextGenerators = new Response().getContextGenerators();

                if (contextGenerators.containsKey(accept)) {
                    generator = contextGenerators.get(accept);
                } else {
                    generator = contextGenerators.get("default");
                }
            }
            if (parameterTypes.length == 1 && arguments.size() == 0 && (request.getMethod().toUpperCase().equals("POST") || request.getMethod().toUpperCase().equals("PUT"))) {
                Parameter parameter = parameterTypes[0];
                System.out.println("request.getRawPost() - " + request.getRawPost());
                methodArguments[0] = valueFromType(parameter, request.getRawPost());
            } else {
                for (int index = 0; index < parameterTypes.length; index++) {
                    Parameter parameter = parameterTypes[index];
                    String value = arguments.get(index);
                    methodArguments[index] = valueFromType(parameter, value);
                }
            }
            
            return (Response) method.invoke(endpoint.getObject(), methodArguments);
        } catch (Exception exception) {
            exception.printStackTrace();
            return new Response().InternalServerError().data(exception.toString());
        }
    }

    private Object valueFromType(Parameter parameter, String value) throws Exception {
        Object obj = value;
        switch (parameter.getType().getTypeName()) {
            case "java.lang.String":
                return value;
            case "java.util.Date":
                return dateFormat.parse(value);
            case "int":
            case "java.lang.Integer":
                return Integer.parseInt(value);
            case "boolean":
            case "java.lang.Boolean":
                return Boolean.parseBoolean(value);
            case "byte":
            case "java.lang.Byte":
                return Byte.parseByte(value);
            case "char":
                return value.charAt(0);
            case "long":
            case "java.lang.Long":
                return Long.parseLong(value);
            case "short":
            case "java.lang.Short":
                return Short.parseShort(value);
            case "float":
            case "java.lang.Float":
                return Float.parseFloat(value);
            case "double":
            case "java.lang.Double":
                return Double.parseDouble(value);
            default:
                System.out.println("generator: " + value);
                return generator.toModel(value, parameter.getType());
        }
    }

    @Override
    public String toString() {
        return "RestAction{" + "arguments=" + arguments + ", endpoint=" + endpoint + '}';
    }

}
