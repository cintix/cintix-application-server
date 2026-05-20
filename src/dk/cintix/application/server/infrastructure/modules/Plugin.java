package dk.cintix.application.server.infrastructure.modules;

/**
 * Public contract for application server plugins.
 *
 * @author cix
 */
public interface Plugin {
    String getName();
    void register(PluginContext context);
}
