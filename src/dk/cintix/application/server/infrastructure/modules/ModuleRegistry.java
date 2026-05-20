package dk.cintix.application.server.infrastructure.modules;

import dk.cintix.application.server.infrastructure.Application;
import dk.cintix.application.server.modules.http.server.HttpModule;
import java.util.ServiceLoader;

/**
 * Composition root that wires modules together.
 * All cross-module wiring and initialization happens here.
 *
 * @author cix
 */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static void initialize() {
        Application.set("APP_INITIALIZED", "true");
    }

    public static PluginContext initialize(HttpModule httpModule, Plugin... plugins) {
        initialize();
        PluginContext context = new PluginContext(httpModule);
        if (plugins != null) {
            for (Plugin plugin : plugins) {
                if (plugin != null) {
                    plugin.register(context);
                }
            }
        }
        return context;
    }

    public static PluginContext loadPlugins(HttpModule httpModule) {
        PluginContext context = initialize(httpModule);
        ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class);
        for (Plugin plugin : loader) {
            plugin.register(context);
        }
        return context;
    }
}
