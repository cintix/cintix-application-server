package dk.cintix.application.server.modules.database.services;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 *
 * @author migo
 */
public class DataSourceManager {

    private static final Logger logger = Logger.getLogger(DataSourceManager.class.getName());

    private static Map<String, DataSource> dataSources;
    private static final String CONTEXT_LOOKUP = "java:comp/env/";

    public static DataSource getInstance(String name) {
        if (dataSources == null) {
            dataSources = new HashMap<>();
        }

        if (dataSources.containsKey(name)) {
            return dataSources.get(name);
        } else {
            try {
                Context ctx = new InitialContext();
                dataSources.put(name, (DataSource) ctx.lookup(CONTEXT_LOOKUP + name));
            } catch (Exception ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.severe(ex.toString());
                }
            }
        }
        return dataSources.get(name);
    }

    public static void addDataSource(String name, DataSource ds) {
        if (dataSources == null) {
            dataSources = new HashMap<>();
        }
        dataSources.put(name, ds);
    }

    public static void removeDataSource(String name) {
        if (dataSources != null && dataSources.containsKey(name)) {
            dataSources.remove(name);
        }
    }
}
