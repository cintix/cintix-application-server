# Repository Guidelines

## Project Structure & Module Organization
Core Java sources live under `src/dk/cintix/application/server`, organized as modular feature/domain modules:
- `modules/http/server` for REST routing, endpoint registration, request/response handling, static files, and WebSocket support.
- `modules/graphql` for the GraphQL plugin contract, endpoint adapter, parser, executor, and registry.
- `modules/openapi` for OpenAPI 3.0 spec generation and the Swagger UI endpoint.
- `modules/mcp` for the Model Context Protocol JSON-RPC endpoint.
- `modules/ratelimit` for the rate limit plugin and HTTP request filter.
- `modules/scheduler` for the scheduler plugin and fixed-rate jobs.
- `modules/database` for datasource/entity/connection management.
- `modules/security` for SSL context and certificate loading.
- `infrastructure/` for shared technical utilities, annotations, plugin contracts, and composition-root wiring.

Build output goes to `build/` and distributables to `dist/`. Third-party jars are vendored in `lib/`. Tests are expected in `test/` (mirroring package paths under `src/`). NetBeans project metadata is in `nbproject/`.

## Plugin System
Optional capabilities are implemented as plugins. Plugins implement `dk.cintix.application.server.infrastructure.modules.Plugin`, register through `PluginContext`, and are wired only in the composition root (`infrastructure/modules/ModuleRegistry.java`). Plugins may be registered directly with `ModuleRegistry.initialize(httpModule, plugins...)` or discovered through Java `ServiceLoader` using `META-INF/services/dk.cintix.application.server.infrastructure.modules.Plugin`.

Current plugin modules:
- `graphql`: use `GraphQLModule graphql = context.getModule(GraphQLModule.class); graphql.addEndpoint("/graphql", serviceOrServices...);`
- `ratelimit`: use `@RateLimitModule.RateLimit` on endpoint methods or classes.
- `scheduler`: use `SchedulerModule` for fixed-rate jobs.

GraphQL is no longer part of HTTP core. Do not add or reintroduce `HttpModule.addGraphQLEndpoint(...)`; use the `GraphQLModule` contract. A single GraphQL endpoint may register multiple service classes:
```java
graphql.addEndpoint("/graphql", new UserQueries(), new ProductQueries(), new OrderMutations());
```

GraphQL query and mutation annotations live on the module contract:
```java
@GraphQLModule.Query("user")
@GraphQLModule.Mutation("createOrder")
```

`modules/openapi` and `modules/mcp` are **not** plugins. They do not implement `Plugin` and are absent from `META-INF/services`; they are handler objects attached in `RestHttpServer` through `enableOpenApi(...)` / `enableMcp(...)`, which call `addEndpoint("/api", ...)`. Do not add them to the `ServiceLoader` list.

## Modular Hybrid Architecture
Use a modular monolith style that combines feature-module ownership, clean public contracts, hidden implementations, and pragmatic service orchestration.

Core rules:
- Organize by feature/domain, not broad horizontal technical layers.
- Each module root contains exactly one `.java` file: the module's public contract interface.
- The module-root contract is the only public entry point into the module.
- Module roots must not contain models, managers, factories, controllers, workers, or implementation classes.
- The module-root contract implementation lives in the module's `services` package and acts as the module facade.
- Implementation lives in `services`.
- Persistence and data access live in `services/persistence`.
- Other modules may depend only on module-root contracts.
- Do not import another module's internals across module boundaries.
- Persistence entities and internal models must not leak outside the module boundary.
- Shared technical code is allowed only in `infrastructure`, and infrastructure must not depend on module internals.
- Only the composition root may instantiate or wire implementations across module boundaries.
- Composition-root wiring belongs in infrastructure, for example `infrastructure/modules/ModuleRegistry.java`.

Standard module layout:
```text
<module>/
  <ModuleContractInterface>.java

  endpoint/

  services/
    <ModuleContractInterface>Service.java

    domain/
      models/
      rules/
      events/

    persistence/
      entities/
      repositories/
      managers/
      mappers/
```

Infrastructure layout:
```text
infrastructure/
  logging/
  persistence/
  annotations/
  modules/
```

Internal dependency rules:
- `endpoint` may call internal services or the module facade service.
- Internal services may depend on `domain` and module-private contracts/interfaces.
- `domain` must not depend on `endpoint`, `persistence`, frameworks, infrastructure, or other module internals.
- `persistence` implements module-private persistence concerns and maps between entities and domain models.
- Other modules may depend only on the module-root contract.
- Module-root contracts expose behavior, not internal entities or state.
- Infrastructure provides technical capabilities but must not contain feature business logic.

Standard dependency flow:
```text
Other Modules
        ↓
Module Contract Interface
        ↓
Module Facade Service
        ↓
Internal Services
        ↓
Domain
        ↓
Persistence
```

When refactoring, map module boundaries and violations first, define module-root contracts, move implementations behind contracts, move persistence into `services/persistence`, extract shared technical code into `infrastructure`, move cross-module wiring to the composition root, verify dependency direction, and remove unnecessary abstractions.

Use these heuristics:
- Keep business capabilities inside the owning feature module.
- Put reusable technical code in `infrastructure` only when it does not know any module internals.
- If another module needs behavior, expose it through the module-root contract.
- If another module needs data, expose behavior instead of sharing entities.
- Put orchestration inside internal services.
- Put framework adapters in `endpoint` and `persistence`.
- Keep persistence entities separate from domain models when behavior or invariants matter.
- Avoid abstractions unless they protect a real boundary or simplify ownership.

## Build, Test, and Development Commands
Use Apache Ant from repo root:

| Command | Purpose |
|---------|---------|
| `ant clean` | Remove `build/` artifacts |
| `ant compile` | Compile Java sources |
| `ant compile-test` | Compile test sources |
| `ant jar` | Create `dist/cintix-application-server.jar` |
| `ant jar-with-dependencies` | Jar + bundled gson → `dist/cintix-application-server-all.jar` (default target) |
| `ant test` | Compiles tests, then reports `No tests executed.` — the suite is not JUnit, so this runs nothing |
| `ant default` | `test,jar,javadoc` — compiles, builds the jar and generates javadoc; runs no tests (the `test` target is a no-op for this suite) |
| `ant run` | Runs `dk.cintix.application.server.Main`, a stub that only prints "library module. No standalone runtime is configured." |

The suite is a plain-Java runner, not JUnit, so it is invoked directly:

```bash
ant compile-test && java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.AllTests
```

A single test class:

```bash
ant compile-test && java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.rest.http.RestHttpServerPathTest
```

Release with `./release.sh` (interactive) or `./release.sh minor "description"` — it bumps `.releases`, builds the fat jar, tags, pushes, and creates a GitHub release.

The build compiles with `--release 8 -parameters` (the `-pre-compile` block in `build.xml`). `-parameters` is load-bearing: GraphQL, MCP, and REST argument binding all read `Parameter.getName()`, so compiling by hand without it silently breaks argument resolution at runtime.

If you work in containers, review `Dockerfile` and `run.sh` before localizing changes (`run-docker.sh` is empty).

## Coding Style & Naming Conventions
- Java 8 target (`javac.source=1.8`, `javac.target=1.8`), compiled with `--release 8` in `build.xml` for portability across JDK 8–25+.
- Use 4-space indentation, UTF-8 encoding, and braces on all control blocks.
- Keep package names lowercase (`dk.cintix...`), classes in `PascalCase`, methods/fields in `camelCase`, constants in `UPPER_SNAKE_CASE`.
- Keep REST annotations and endpoint classes close to related HTTP logic in `rest/`.

## Testing Guidelines
- Place tests in `test/` with matching package structure (example: `test/dk/cintix/application/server/rest/...`).
- Name test classes `*Test.java`. Each is a plain class with a public `runAll()` that calls private, behavior-named test methods (`generatesSpecWithTitleAndVersion`, `internalServiceError_returnsInternalErrorWithoutDetails`), one assertion style throughout.
- Assert with `TestSupport` (`assertTrue`, `assertFalse`, `assertEquals`, `assertArrayEquals`, `assertNull`) — there is no JUnit on the classpath. Add new helpers there rather than pulling in a framework.
- Structure each test with explicit `// Arrange`, `// Act`, `// Assert` comment blocks.
- **Register every new test class in `AllTests.main()`** (`test/dk/cintix/application/server/AllTests.java`). A class that is not listed there never runs, and neither `ant test` nor `ant default` will reveal the omission.
- Run the suite with the `java -cp ...` command above before opening a PR. Add regression tests for fixes in `rest`, `jdbc`, or SSL handling.

## Commit & Pull Request Guidelines
Recent history favors short, imperative commit subjects (examples: `removed debug`, `changed dependencies`). Prefer clearer variants like `Remove debug logging from RestHttpServer`.

For pull requests:
- Explain what changed and why.
- Link related issues/tasks.
- List verification steps and commands run (for example, `ant compile-test && java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.AllTests`).
- Include API behavior notes or sample request/response when endpoint behavior changes.
