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

Tests use a custom assertion framework in `TestSupport` (no JUnit); each test class has a `runAll()` method. `AllTests` is the test suite runner. Tests follow the AAA pattern with explicit `// Arrange`, `// Act`, `// Assert` comment blocks.

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

#### Parameter injection (mixed path + body)

`RestActionService.process()` resolves method parameters as follows:
- **Single param, no path arguments, POST/PUT** → raw body is deserialized into the parameter.
- **Path arguments present** → each parameter up to `arguments.size()` gets the corresponding URL argument; any remaining parameters receive `request.getRawPost()`. This means methods like `createItem(String spaceId, String body)` work — `spaceId` comes from the path, `body` gets the raw post content.

#### Header handling

`RestHttpRequest.getHeader(key)` is **case-insensitive** — it iterates all stored headers using `equalsIgnoreCase()`. `addHeader(key, value)` normalizes the key to uppercase on storage for consistency with `HttpUtil.parseHeaderKeys`. Regardless of how a header was stored (parsed, added manually, or from an upgrade request), `getHeader("Authorization")` will find it.

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

### WebSocket

WebSocket handlers are registered via `webSocketService.register(path, handler)` with lifecycle annotations (`@OnOpen`, `@OnMessage`, `@OnBinary`, `@OnClose`, `@OnError`). The `WebSocketSession` object is passed as the first argument to all lifecycle methods.

Query strings from the upgrade request are copied to session attributes with a `qs.` prefix. A client connecting to `/ws/chat?token=abc&room=general` can read `session.getAttribute("qs.token")` → `"abc"` and `session.getAttribute("qs.room")` → `"general"` in its `@OnOpen` handler.

### Vendored libraries

- `lib/gson-2.8.6.jar` — JSON serialization (also bundled into `cintix-application-server-all.jar`)
- `lib/postgresql-42.2.8.jar` — PostgreSQL JDBC driver
- `lib/cintix-html-engine.jar` — HTML server-page engine from sibling project `../cintix-html-engine`

### Java 8 target

Source and target are Java 1.8 (`javac.source=1.8`, `javac.target=1.8`). No lambdas/streams used extensively — reflection-heavy patterns for annotation processing and dependency injection.

To produce portable bytecode that runs on any Java 8 through 25+ JRE, the build uses `--release 8` (configured in `build.xml`). This ensures covariant return types like `ByteBuffer.clear()` resolve to Java 8 signatures regardless of which JDK version performs the compilation.

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

## Recent Changes

### WebSocket lifecycle robustness (2026-05-27)

Three fixes that together make the WebSocket layer production-ready:

1. **Broadcaster resilience** — `WebSocketBroadcaster.broadcast()` wraps `session.send()` in try-catch. A stale WebSocket session (disconnected at the TCP level but not yet removed from the active session list) can throw `CancelledKeyException` from the NIO layer. Before this fix, one stale session broke the entire broadcast loop. Now the broadcaster catches the exception per-session, unregisters the stale session, and continues broadcasting to all remaining healthy sessions.

2. **IOException cleanup** — `RestHttpServer.handleRead()` now catches `IOException` (and detects `read == -1`) in the WebSocket path. Previously an IOException from a dropped client was swallowed by the outer catch and the session was never cleaned up. Now it calls `handleDisconnect` immediately — session unregistered from broadcaster, key cancelled, channel closed.

3. **Keepalive ping/pong** — A daemon thread (`ws-keepalive`) sends OP_PING every 30 seconds to all WebSocket connections and closes any session that hasn't responded with pong within 10 seconds. This also sweeps for stale sessions where `isOpen() == false` and removes them from the broadcaster. Without this, proxies and firewalls can silently drop idle connections.

