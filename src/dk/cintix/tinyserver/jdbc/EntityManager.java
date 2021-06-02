package dk.cintix.tinyserver.jdbc;

import dk.cintix.tinyserver.jdbc.annotations.Entity;
import dk.cintix.tinyserver.jdbc.annotations.InjectConnection;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author migo
 */
public class EntityManager {

    /**
     *
     * @param <T>
     * @param instance
     * @return Generic entity
     */
    public static <T> T create(Class<T> instance) {
        T entityManager = (T) getEntityManager(instance);
        if (entityManager != null) {
            return entityManager;
        }
        return null;
    }

    public static <T> T create(Class<T> instance, Connection connection) {
        T entityManager = (T) getEntityManager(instance);
        if (entityManager != null) {
            Field[] fields = entityManager.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(InjectConnection.class) && field.getType().isAssignableFrom(Connection.class)) {
                    field.setAccessible(true);
                    try {
                        if (connection != null && !connection.isClosed()) {
                            field.set(entityManager, connection);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | SQLException ex) {
                        Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
            return entityManager;
        }
        return null;
    }

    public static <T> T create(Class<T> instance, TransactionableConnection connection) {
        T entityManager = (T) getEntityManager(instance);
        if (entityManager != null) {
            Field[] fields = entityManager.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(InjectConnection.class) && field.getType().isAssignableFrom(Connection.class)) {
                    field.setAccessible(true);
                    try {
                        if (connection != null && !connection.isClosed()) {
                            field.set(entityManager, connection);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | SQLException ex) {
                        Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
            return entityManager;
        }
        return null;
    }

    /**
     *
     * @param <T>
     * @param instance
     * @return
     */
    public static <T> T instance(Class<T> instance) {
        T entityManager = null;
        try {
            entityManager = instance.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return entityManager;
    }

    /**
     *
     * @param <T>
     * @param instance
     * @param connection
     * @return
     */
    public static <T> T instance(Class<T> instance, TransactionableConnection connection) {
        try {
            T entityManager = (T) instance.newInstance();
            Field[] fields = entityManager.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(InjectConnection.class) && field.getType().isAssignableFrom(connection.getClass())) {
                    field.setAccessible(true);
                    try {
                        if (connection != null && !connection.isClosed()) {
                            field.set(entityManager, connection);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | SQLException ex) {
                        Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            return entityManager;
        } catch (InstantiationException ex) {
            Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @param <T>
     * @param instance
     * @param connection
     * @return
     */
    public static <T> T instance(Class<T> instance, Connection connection) {
        try {
            T entityManager = (T) instance.newInstance();
            Field[] fields = entityManager.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(InjectConnection.class) && field.getType().isAssignableFrom(connection.getClass())) {
                    field.setAccessible(true);
                    try {
                        if (connection != null && !connection.isClosed()) {
                            field.set(entityManager, connection);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | SQLException ex) {
                        Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            return entityManager;
        } catch (InstantiationException ex) {
            Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @param <T>
     * @param instance
     * @param instanceCount
     * @return array Generic entity
     */
    public static <T> List<T> create(Class<T> instance, int instanceCount) {
        List<T> managers = new ArrayList<>();
        for (int index = 0; index < instanceCount; index++) {
            T manager = create(instance);
            if (manager != null) {
                managers.add(manager);
            }
        }
        return managers;
    }

    /**
     *
     * @param <T>
     * @param entity
     * @return Generic Type
     */
    private static <T> T getEntityManager(Class<T> entity) {
        if (entity.isAnnotationPresent(Entity.class)) {
            Entity annotation = entity.getAnnotation(Entity.class);
            try {
                @SuppressWarnings("unchecked")
                T manager = (T) annotation.manager().newInstance();
                return manager;
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(EntityManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            Logger.getLogger(EntityManager.class.getName()).log(Level.WARNING, "{0} is not a Entity, no annotation is present", entity.getName());
        }
        return null;
    }

}
