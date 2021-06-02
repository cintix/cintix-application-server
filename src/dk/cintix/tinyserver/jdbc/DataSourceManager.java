package dk.cintix.tinyserver.jdbc;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;

/**
 *
 * @author migo
 */
public class DataSourceManager {

    private static final Logger logger = Logger.getLogger(DataSourceManager.class.getName());

    private static Map<String, javax.sql.DataSource> dataSources;
    private static final String CONTEXT_LOOKUP = "java:comp/env/";

    /**
     * Get a Instance of a DataSource
     *
     * @param name
     *
     * @return {@link javax.sql.DataSource}
     */
    public static javax.sql.DataSource getInstance(String name) {
        if (dataSources == null) {
            dataSources = new HashMap<>();
        }

        if (dataSources.containsKey(name)) {
            return dataSources.get(name);
        } else {
            try {
                Context ctx = new InitialContext();
                dataSources.put(name, (javax.sql.DataSource) ctx.lookup(CONTEXT_LOOKUP + name));
            } catch (Exception ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.severe(ex.toString());
                }
            }
        }
        return dataSources.get(name);
    }

    /**
     * Add a DataSource to the Manager
     *
     * @param name name of the datasource
     * @param ds {@link javax.sql.DataSource}
     */
    public static void addDataSource(String name, javax.sql.DataSource ds) {
        if (dataSources == null) {
            dataSources = new HashMap<>();
        }
        dataSources.put(name, ds);
    }

    /**
     * Remove a datasource from the manager
     *
     * @param name
     */
    public static void removeDataSource(String name) {
        if (dataSources != null && dataSources.containsKey(name)) {
            dataSources.remove(name);
        }
    }
}
