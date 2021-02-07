/*
 */
package dk.cintix.tinyserver.rest;

import dk.cintix.tinyserver.rest.response.Response;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 *
 * @author cix
 */
public class RestAction {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
    private final List<String> arguments;
    private final RestEndpoint endpoint;

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

    public Response process() {
        try {
            Method method = endpoint.getMethod();
            Parameter[] parameterTypes = method.getParameters();
            Object[] methodArguments = new Object[parameterTypes.length];

            for (int index = 0; index < parameterTypes.length; index++) {
                Parameter parameter = parameterTypes[index];
                String value = arguments.get(index);
                methodArguments[index] = valueFromType(parameter.getType().getTypeName(), value);
            }

            return (Response) method.invoke(endpoint.getObject(), methodArguments);
        } catch (Exception exception) {
            exception.printStackTrace();
            return new Response().InternalServerError().data(exception.toString());
        }
    }

    private Object valueFromType(String type, String value) throws Exception {
        Object obj = value;
        switch (type) {
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
        }
        return obj;
    }

    @Override
    public String toString() {
        return "RestAction{" + "arguments=" + arguments + ", endpoint=" + endpoint + '}';
    }

}
