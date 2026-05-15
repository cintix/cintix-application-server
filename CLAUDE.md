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
ant compile-test && java -cp build/classes:build/test/classes:lib/* dk.cintix.application.server.AllTests
```

**Run a single test file:**
```bash
ant compile-test && java -cp build/classes:build/test/classes:lib/* dk.cintix.application.server.rest.http.RestHttpServerPathTest
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

- `rest/http/` — NIO server loop, request parsing, HTTP session management
- `rest/` — `RestAction`, `RestEndpoint`, `RestClient`, annotation definitions
- `rest/response/` — `Response` builder (fluent API: `.OK().ContentType(...).model(obj)`)
- `rest/jsd/` — JSON Service Description engine (auto-generates API docs at `?jsd`)
- `jdbc/` — `EntityManager` (annotation-based ORM), `PooledDataSource`, `TransactionableConnection`, `DataSourceManager` (JNDI lookup)
- `ssl/` — `SSLContextManager` loads JKS keystore, creates TLS context
- `io/` — `ReflectionUtil`, `Cache`, `ByteMemoryStream`
- `model/generators/` — `ModelGenerator` interface with `JSONGenerator` (Gson) and `TextGenerator` implementations

### Static file serving

Served from `DOCUMENT_ROOT` (default `"web"`, configurable via `setDocumentRoot()`). HTML files (`.htm`/`.html`) are processed through `cintix-html-engine` for server-page rendering with request parameters merged as template properties. A jail check prevents directory traversal.

### Vendored libraries

- `lib/gson-2.8.6.jar` — JSON serialization (also bundled into `cintix-application-server-all.jar`)
- `lib/postgresql-42.2.8.jar` — PostgreSQL JDBC driver
- `lib/cintix-html-engine.jar` — HTML server-page engine from sibling project `../cintix-html-engine`

### Java 8 target

Source and target are Java 1.8 (`javac.source=1.8`, `javac.target=1.8`). No lambdas/streams used extensively — reflection-heavy patterns for annotation processing and dependency injection.

## Plugin Architecture (Future)

A plugin system was considered to keep the server lightweight — load only what you need.

### Decision: not yet

The current structure already achieves the goal. Domain-specific modules (`graphql`, `database`) are cleanly separated packages. If you don't call `addGraphQLEndpoint`, GraphQL costs nothing at runtime. The only cost is JVM classpath presence, which for two plugins is negligible.

### When to implement

A plugin system becomes worth it when:

- **Jar size matters** — database module pulls `postgresql-42.2.8.jar` (~900KB). Only include it if you use it.
- **Independent versioning** — ship `graphql-plugin-1.1.jar` without touching core.
- **Third-party contributions** — someone builds an auth plugin and publishes it independently.

### Proposed design (when the time comes)

```
lib/
  cintix-graphql-plugin.jar   ← contains META-INF/services/dk...Plugin
  cintix-jdbc-plugin.jar
```

**Plugin interface:**
```java
public interface Plugin {
    void register(HttpModule server);
}
```

Loaded via `java.util.ServiceLoader` in `ModuleRegistry.initialize()`. Each plugin registers itself by calling the appropriate `server.add*()` methods.

**What stays core (always):**
- NIO event loop + HTTP parsing
- REST annotations + routing
- WebSocket (HTTP upgrade, protocol-level)
- SSL/TLS (transport, protocol-level)
- Static file serving + MimeTypes
- Infrastructure (Cache, ReflectionUtil, Status, Application)

**What becomes plugins:**
- GraphQL — query language, not every app needs it
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
- Currently only two candidates — overhead may exceed benefit
