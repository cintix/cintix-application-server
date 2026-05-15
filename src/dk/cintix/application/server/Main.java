package dk.cintix.application.server;

import dk.cintix.application.server.infrastructure.modules.ModuleRegistry;

public class Main {

    public static void main(String[] args) {
        ModuleRegistry.initialize();
        System.out.println("cintix-application-server library module. No standalone runtime is configured.");
    }
}
