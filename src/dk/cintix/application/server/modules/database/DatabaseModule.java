package dk.cintix.application.server.modules.database;

import javax.sql.DataSource;

/**
 * Public contract for the database module.
 *
 * @author migo
 */
public interface DatabaseModule {
    DataSource getInstance(String name);
    void addDataSource(String name, DataSource ds);
    void removeDataSource(String name);
}
