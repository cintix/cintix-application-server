package dk.cintix.application.server.infrastructure.modules;

import dk.cintix.application.server.infrastructure.Application;

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
}
