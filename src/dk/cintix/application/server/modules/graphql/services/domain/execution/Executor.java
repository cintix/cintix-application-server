package dk.cintix.application.server.modules.graphql.services.domain.execution;

import dk.cintix.application.server.modules.graphql.services.domain.GraphQLException;
import dk.cintix.application.server.modules.graphql.services.domain.ast.*;
import dk.cintix.application.server.modules.graphql.services.domain.registry.GraphQLRegistry;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Executor {

    private static final Logger logger = Logger.getLogger(Executor.class.getName());

    private final GraphQLRegistry registry;

    public Executor(GraphQLRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> execute(Document doc) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Selection sel : doc.getOperation().getSelections()) {
            Method method = resolveMethod(doc.getOperation().getType(), sel.getName());
            if (method == null) {
                throw new GraphQLException("Unknown operation: " + sel.getName());
            }
            Object service = registry.getService(method);
            Object[] args = mapArguments(method, sel);
            try {
                Object value = method.invoke(service, args);
                result.put(sel.getName(), projectSubSelection(value, sel.getSubSelections()));
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                logger.log(Level.SEVERE, "GraphQL method execution failed for " + sel.getName(), cause);
                throw new RuntimeException(cause);
            } catch (IllegalAccessException e) {
                logger.log(Level.SEVERE, "GraphQL method is not accessible for " + sel.getName(), e);
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private Method resolveMethod(OperationType type, String name) {
        return type == OperationType.QUERY ? registry.getQuery(name) : registry.getMutation(name);
    }

    private Object[] mapArguments(Method method, Selection selection) {
        Map<String, Value> argsMap = selection.getArguments();
        Parameter[] params = method.getParameters();

        Set<String> parameterNames = new HashSet<>();
        for (Parameter p : params) {
            parameterNames.add(p.getName());
        }

        for (String supplied : argsMap.keySet()) {
            if (!parameterNames.contains(supplied)) {
                throw new GraphQLException("Unknown argument \"" + supplied + "\" for " + selection.getName());
            }
        }

        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            String name = p.getName();
            if (!argsMap.containsKey(name)) {
                throw new GraphQLException("Missing argument \"" + name + "\" for " + selection.getName());
            }
            args[i] = convertValue(argsMap.get(name), p.getType(), name);
        }
        return args;
    }

    private Object convertValue(Value val, Class<?> targetType, String name) {
        if (val == null || val instanceof NullValue) {
            if (targetType.isPrimitive()) {
                throw new GraphQLException("Argument \"" + name + "\" must not be null");
            }
            return null;
        }

        if (targetType == String.class) {
            if (val instanceof StringValue) {
                return ((StringValue) val).getText();
            }
            throw new GraphQLException("Argument \"" + name + "\" must be a string");
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (val instanceof BooleanValue) {
                return ((BooleanValue) val).getValue();
            }
            throw new GraphQLException("Argument \"" + name + "\" must be a boolean");
        }

        if (isIntegerType(targetType)) {
            if (val instanceof NumberValue) {
                String text = ((NumberValue) val).getText();
                try {
                    if (targetType == Byte.class || targetType == byte.class) return Byte.parseByte(text);
                    if (targetType == Short.class || targetType == short.class) return Short.parseShort(text);
                    if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(text);
                    if (targetType == Long.class || targetType == long.class) return Long.parseLong(text);
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Argument \"" + name + "\" must be an integer");
                }
            }
            throw new GraphQLException("Argument \"" + name + "\" must be an integer");
        }

        if (isDecimalType(targetType)) {
            if (val instanceof NumberValue) {
                String text = ((NumberValue) val).getText();
                try {
                    if (targetType == Float.class || targetType == float.class) return Float.parseFloat(text);
                    if (targetType == Double.class || targetType == double.class) return Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Argument \"" + name + "\" must be a decimal number");
                }
            }
            throw new GraphQLException("Argument \"" + name + "\" must be a decimal number");
        }

        if (targetType.isEnum()) {
            if (val instanceof EnumValue) {
                try {
                    return Enum.valueOf((Class<? extends Enum>) targetType, ((EnumValue) val).getName());
                } catch (IllegalArgumentException e) {
                    throw new GraphQLException("Invalid value \"" + ((EnumValue) val).getName() + "\" for argument \"" + name + "\"");
                }
            }
            throw new GraphQLException("Argument \"" + name + "\" must be an enum");
        }

        if (targetType == Object.class) {
            return convertObjectLikeValue(val, name);
        }

        if (val instanceof ObjectValue) {
            return convertObjectValue((ObjectValue) val, targetType, name);
        }

        throw new GraphQLException("Argument \"" + name + "\" has unsupported type " + targetType.getName());
    }

    private Object convertObjectLikeValue(Value val, String name) {
        if (val == null || val instanceof NullValue) return null;
        if (val instanceof StringValue) return ((StringValue) val).getText();
        if (val instanceof BooleanValue) return ((BooleanValue) val).getValue();
        if (val instanceof EnumValue) return ((EnumValue) val).getName();
        if (val instanceof NumberValue) return new BigDecimal(((NumberValue) val).getText());
        if (val instanceof ObjectValue) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, Value> e : ((ObjectValue) val).getFields().entrySet()) {
                map.put(e.getKey(), convertObjectLikeValue(e.getValue(), name + "." + e.getKey()));
            }
            return map;
        }
        throw new GraphQLException("Argument \"" + name + "\" has an invalid value");
    }

    private Object convertObjectValue(ObjectValue val, Class<?> targetType, String name) {
        if (targetType.isPrimitive() || targetType.isEnum()) {
            throw new GraphQLException("Argument \"" + name + "\" cannot be an object");
        }

        Object obj;
        try {
            Constructor<?> constructor = targetType.getDeclaredConstructor();
            constructor.setAccessible(true);
            obj = constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new GraphQLException("Argument \"" + name + "\" has no default constructor");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Map.Entry<String, Value> e : val.getFields().entrySet()) {
            Field field = findField(targetType, e.getKey());
            if (field == null) {
                throw new GraphQLException("Unknown field \"" + e.getKey() + "\" for argument \"" + name + "\"");
            }
            try {
                field.setAccessible(true);
                field.set(obj, convertValue(e.getValue(), field.getType(), name + "." + e.getKey()));
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
        return obj;
    }

    private boolean isIntegerType(Class<?> type) {
        return type == Byte.class || type == byte.class
                || type == Short.class || type == short.class
                || type == Integer.class || type == int.class
                || type == Long.class || type == long.class;
    }

    private boolean isDecimalType(Class<?> type) {
        return type == Float.class || type == float.class
                || type == Double.class || type == double.class;
    }

    private Object projectSubSelection(Object obj, List<Selection> subs) {
        if (obj == null) return null;
        if (subs == null || subs.isEmpty()) return obj;
        Map<String, Object> map = new LinkedHashMap<>();
        for (Selection sel : subs) {
            Field field = findField(obj.getClass(), sel.getName());
            if (field == null) {
                throw new GraphQLException("Unknown field \"" + sel.getName() + "\"");
            }
            try {
                field.setAccessible(true);
                Object val = field.get(obj);
                map.put(sel.getName(), projectSubSelection(val, sel.getSubSelections()));
            } catch (IllegalAccessException e) {
                logger.log(Level.SEVERE, "Failed to access field " + sel.getName(), e);
                throw new RuntimeException(e);
            }
        }
        return map;
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue searching superclasses.
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
