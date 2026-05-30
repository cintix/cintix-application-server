package dk.cintix.application.server.modules.graphql.services.domain.execution;

import dk.cintix.application.server.modules.graphql.services.domain.ast.*;
import dk.cintix.application.server.modules.graphql.services.domain.registry.GraphQLRegistry;

import java.lang.reflect.*;
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
        Map<String, Object> result = new HashMap<>();
        for (Selection sel : doc.getOperation().getSelections()) {
            Method method = resolveMethod(doc.getOperation().getType(), sel.getName());
            if (method == null) throw new RuntimeException("No method registered for " + sel.getName());
            Object service = registry.getService(method);
            Object[] args = mapArguments(method, sel.getArguments());
            try {
                Object value = method.invoke(service, args);
                result.put(sel.getName(), projectSubSelection(value, sel.getSubSelections()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private Method resolveMethod(OperationType type, String name) {
        return type == OperationType.QUERY ? registry.getQuery(name) : registry.getMutation(name);
    }

    private Object[] mapArguments(Method method, Map<String, Value> argsMap) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Value val = argsMap.get(p.getName());
            args[i] = convertValue(val, p.getType());
        }
        return args;
    }

    private Object convertValue(Value val, Class<?> targetType) {
        if (val == null) return null;
        if (val instanceof StringValue) return ((StringValue) val).getText();
        if (val instanceof NumberValue) return Integer.parseInt(((NumberValue) val).getText());
        if (val instanceof BooleanValue) return ((BooleanValue) val).getValue();
        if (val instanceof EnumValue) return Enum.valueOf((Class<Enum>) targetType, ((EnumValue) val).getName());
        if (val instanceof ObjectValue) {
            try {
                Object obj = targetType.getDeclaredConstructor().newInstance();
                ObjectValue o = (ObjectValue) val;
                for (Map.Entry<String, Value> e : o.getFields().entrySet()) {
                    Field f = targetType.getDeclaredField(e.getKey());
                    f.setAccessible(true);
                    f.set(obj, convertValue(e.getValue(), f.getType()));
                }
                return obj;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (val instanceof NullValue) return null;
        throw new RuntimeException("Cannot convert value to " + targetType.getName());
    }

    private Object projectSubSelection(Object obj, List<Selection> subs) {
        if (obj == null) return null;
        if (subs.isEmpty()) return obj;
        Map<String, Object> map = new HashMap<>();
        for (Selection sel : subs) {
            try {
                Field f = obj.getClass().getDeclaredField(sel.getName());
                f.setAccessible(true);
                Object val = f.get(obj);
                map.put(sel.getName(), projectSubSelection(val, sel.getSubSelections()));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.log(Level.FINE, "Skipping missing or inaccessible field: " + sel.getName(), e);
            }
        }
        return map;
    }
}
