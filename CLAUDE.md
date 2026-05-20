# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Apache Ant from repo root:

| Command | Purpose |
|---------|---------|
| `ant clean` | Remove `build/` artifacts |
| `ant compile` | Compile Java sources |
| `ant jar` | Create `dist/cintix-application-server.jar` |
| `ant jar-with-dependencies` | Jar + bundled gson (default target) |
| `ant compile-test` | Compile test sources |
| `ant test` | Compile test sources only (does not execute) |

**Run all tests:**
```bash
ant compile-test && java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.AllTests
```

**Run a single test file:**
```bash
ant compile-test && java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.rest.http.RestHttpServerPathTest
```

Tests use a custom assertion framework in `TestSupport` (no JUnit); each test class has a `runAll()` method. `AllTests` is the test suite runner.

## Architecture

This is a lightweight, annotation-driven Java 8 REST application server built on `java.nio` (non-blocking I/O with `Selector`/`SocketChannel`). No servlet container or external framework.

### Request lifecycle

1. `RestHttpServer` (abstract, NIO event loop) accepts connections on a `ServerSocketChannel`
2. Raw HTTP is parsed into `RestHttpRequest` (headers, query strings, post fields, body stream)
3. Request is matched against registered endpoints via exact path lookup first, then regex patterns (longest pattern first)
4. `RestAction.process()` invokes the matched method — injects `@Inject` fields, converts path/regex arguments to typed parameters via `ReflectionUtil.valueFromType()`, handles `@Cache`/`@Static`/`@CacheByStatus` caching
5. A `Response` builder is returned and written back over the socket

### Annotation-driven routing

Endpoints are registered via `server.addEndpoint(path, object)`. Methods use:
- `@Action(path = "/...")` — marks a handler method
- `@GET`, `@POST`, `@PUT`, `@DELETE` — HTTP method binding
- `@Inject` — inject `RestHttpRequest` into endpoint fields
- `@Cache`, `@Static`, `@CacheByStatus` — response caching strategies

The regex router supports path parameters using `:paramName` → `([^/]+)` substitution.

### Key packages

- `modules/http/server/` — HTTP module contract, NIO server loop, request parsing, endpoint registration, static files, WebSocket support
- `modules/http/server/services/` — REST action dispatch, JSON service description, response models, model generators
- `modules/graphql/` — GraphQL plugin contract; register endpoints with `graphql.addEndpoint(...)`
- `modules/graphql/endpoint/` — HTTP adapter for GraphQL POST requests
- `modules/graphql/services/domain/` — GraphQL lexer/parser/AST/executor/registry internals
- `modules/ratelimit/` — rate limit plugin and `@RateLimitModule.RateLimit`
- `modules/scheduler/` — scheduler plugin and fixed-rate jobs
- `modules/database/` — `EntityManager` (annotation-based ORM), `PooledDataSource`, `TransactionableConnection`, `DataSourceManager` (JNDI lookup)
- `modules/security/` — `SSLContextManager` loads JKS keystore, creates TLS context
- `infrastructure/` — `ReflectionUtil`, `Cache`, `ByteMemoryStream`, REST annotations, plugin contracts, `ModuleRegistry`

### Static file serving

Served from `DOCUMENT_ROOT` (default `"web"`, configurable via `setDocumentRoot()`). HTML files (`.htm`/`.html`) are processed through `cintix-html-engine` for server-page rendering with request parameters merged as template properties. A jail check prevents directory traversal.

### Vendored libraries

- `lib/gson-2.8.6.jar` — JSON serialization (also bundled into `cintix-application-server-all.jar`)
- `lib/postgresql-42.2.8.jar` — PostgreSQL JDBC driver
- `lib/cintix-html-engine.jar` — HTML server-page engine from sibling project `../cintix-html-engine`

### Java 8 target

Source and target are Java 1.8 (`javac.source=1.8`, `javac.target=1.8`). No lambdas/streams used extensively — reflection-heavy patterns for annotation processing and dependency injection.

## Plugin Architecture

The server has a lightweight plugin system for optional capabilities.

Plugins implement `Plugin` and are wired in `ModuleRegistry` through a `PluginContext`. Plugins can be passed directly to `ModuleRegistry.initialize(httpModule, plugins...)` or discovered with `ServiceLoader` through `META-INF/services/dk.cintix.application.server.infrastructure.modules.Plugin`.

Current plugin modules:
- `graphql` — register with `GraphQLModule graphql = context.getModule(GraphQLModule.class); graphql.addEndpoint("/graphql", serviceOrServices...);`
- `ratelimit` — request filter with `@RateLimitModule.RateLimit`
- `scheduler` — fixed-rate scheduled jobs via `SchedulerModule`

GraphQL is no longer exposed through HTTP core. Do not reintroduce `HttpModule.addGraphQLEndpoint(...)`. Use:
```java
PluginContext context = ModuleRegistry.initialize(httpModule, new GraphQLModuleService());
GraphQLModule graphql = context.getModule(GraphQLModule.class);
graphql.addEndpoint("/graphql", new UserQueries(), new ProductQueries(), new OrderMutations());
```

GraphQL service classes annotate methods through the module contract:
```java
@GraphQLModule.Query("user")
@GraphQLModule.Mutation("createOrder")
```

Potential external jar layout:

```
lib/
  cintix-graphql-plugin.jar   ← contains META-INF/services/dk...Plugin
  cintix-jdbc-plugin.jar
```

**Plugin interface:**
```java
public interface Plugin {
    String getName();
    void register(PluginContext context);
}
```

Loaded via `java.util.ServiceLoader` in `ModuleRegistry.loadPlugins(httpModule)`. Each plugin registers its module contract and any HTTP extension points through `PluginContext`.

**What stays core (always):**
- NIO event loop + HTTP parsing
- REST annotations + routing
- WebSocket (HTTP upgrade, protocol-level)
- SSL/TLS (transport, protocol-level)
- Static file serving + MimeTypes
- Infrastructure (Cache, ReflectionUtil, Status, Application)

**What may become a plugin next:**
- JDBC/Database — ORM + pooling, not every app has a database

### Pros
- Core jar stays small (~100KB without plugins)
- Each plugin independently versioned and released
- Third parties can contribute plugins without touching core
- Clear boundary: core never imports from plugin code

### Cons
- `ServiceLoader` discovery adds complexity
- Plugin authors must follow the module contract
- Cross-plugin dependencies (e.g. auth needs database) require careful design
