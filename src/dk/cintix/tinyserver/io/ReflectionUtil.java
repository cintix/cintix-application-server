package dk.cintix.tinyserver.io;

import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;

/**
 *
 * @author migo
 */
public class ReflectionUtil {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");

    public static Object valueFromType(Parameter parameter, String value) throws Exception {
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
                return null;
        }
    }
}
