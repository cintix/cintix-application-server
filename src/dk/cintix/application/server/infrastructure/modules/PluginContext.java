package dk.cintix.application.server.infrastructure.modules;

import dk.cintix.application.server.modules.http.server.HttpModule;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composition context shared with plugins during registration.
 *
 * @author cix
 */
public class PluginContext {
    private final HttpModule httpModule;
    private final Map<Class<?>, Object> modules = new LinkedHashMap<>();

    public PluginContext(HttpModule httpModule) {
        this.httpModule = httpModule;
    }

    public HttpModule getHttpModule() {
        return httpModule;
    }

    public <T> void registerModule(Class<T> contract, T module) {
        modules.put(contract, module);
    }

    public <T> T getModule(Class<T> contract) {
        Object module = modules.get(contract);
        if (module == null) {
            return null;
        }
        return contract.cast(module);
    }
}
