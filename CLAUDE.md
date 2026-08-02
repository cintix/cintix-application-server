# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Release Process

```bash
./release.sh              # interactive: prompts for major/minor/bugfix + description
./release.sh minor "description"   # non-interactive
```

The script: (1) bumps the version in `.releases`, (2) builds the fat jar, (3) git tags, (4) pushes, (5) creates a GitHub release with `gh`. See `.releases` for version history.

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

### Request timeout (2026-05-30)

Implemented two-level request timeout:

- **System-wide default** (30s): configured via `setDefaultRequestTimeoutMs(int ms)`. Checked in `WorkerTask.run()` before processing — if `elapsed > timeout`, returns `408 Request Timeout` immediately without invoking the endpoint. Use `setDefaultRequestTimeoutMs(0)` to disable globally.
- **Per-endpoint `@Timeout` annotation**: `@Timeout(ms = 120_000)` on a method or class overrides the global default. `@Timeout(ms = 0)` disables timeout for that endpoint (e.g. CSV exports, streaming). The annotation is checked against exact path match and regex patterns, same as `@RateLimit`.
- **Idle read timeout** (60s): `sweepIdleConnections()` runs every 30s in the event loop, iterating `selector.keys()` and closing connections that haven't completed their HTTP read within `idleReadTimeoutMs` (configurable via `setIdleReadTimeoutMs()`). WebSocket connections are skipped.
- Added `Response.RequestTimeout()` and `408 Request Timeout` to `messageFromStatus()`.

### Health-check endpoint (2026-05-30)

Added a built-in `GET /health` endpoint that bypasses the worker pool entirely:

- **Event loop inline**: health checks run directly on the NIO event loop — they respond even when all worker threads are saturated. The `handleRead()` method detects `/health` requests and calls `executeHealthCheck()` before reaching the `workerPool.submit()` path.
- **Pluggable probes**: register health checks via `server.addHealthCheck(new HealthCheck() { ... })`. Each probe returns `"UP"` or an error message. The built-in uptime probe is always included.
- **JSON response**: `{"status":"UP","database":"UP","uptime":"5m"}` with 200 OK when all probes pass. `{"status":"DOWN","redis":"Connection refused","uptime":"5m"}` with 503 when any probe fails.
- **Configurable**: `setHealthPath(String path)` changes the health endpoint path (default `/health`).

### Graceful shutdown (2026-05-30)

Implemented a six-phase graceful shutdown triggered when `setRunning(false)` is called:

1. **Stop accepting**: cancel the `ServerSocketChannel` selection key — no new TCP connections
2. **Drain worker pool**: `workerPool.shutdown()` + `awaitTermination(drainTimeoutMs)` — let in-flight workers complete
3. **Process completed tasks**: drain the `completionQueue` one final time
4. **Drain pending writes**: short `select(100ms)` loop processes `OP_READ` and `OP_WRITE` for remaining connections. During drain, `shouldKeepAlive()` returns false so all responses get `Connection: close`.
5. **Force-close remaining**: after drain timeout, any remaining connections are forcibly closed. Logged at WARNING level.
6. **Close resources**: `selector.close()` + `serverSocketChannel.close()`

Configurable via `setDrainTimeoutMs(int ms)` (default 10s). Full lifecycle logged at INFO/WARNING levels.

### Rate limiting (2026-05-30)

Enhanced the existing `ratelimit` plugin from annotation-only to two-level:

- **Global default** (off by default): `rateLimitModule.setEnabled(true)` enables a global rate limit (default 100 req/60s). Configure via `setDefaultRequests()`, `setDefaultPerSeconds()`, `setDefaultKeyHeader()`.
- **Per-endpoint override**: `@RateLimit(requests=N, perSeconds=S)` overrides the global setting for that endpoint. Stricter (`requests=10`), more lenient (`requests=500`), or `requests=0` to whitelist (no limit).
- **Memory leak fix**: background daemon thread (`rate-limit-cleanup`) periodically removes expired windows. Aggressive sweep triggers at 10K+ windows.
- **Shutdown**: `shutdown()` stops the cleanup thread and clears all windows.
- **Backwards compatible**: without `setEnabled(true)` or `@RateLimit` annotation, no rate limiting occurs.

### HTTP/1.1 compliance (2026-05-30)

Implemented three HTTP/1.1 requirements:

- **Host header validation** (RFC 7230 §5.4): Requests without a `Host` header get a `400 Bad Request` response. Checked inline in the event loop before worker dispatch (fast path).
- **Gzip compression**: Transparent response compression when client sends `Accept-Encoding: gzip`. Applied in `handleWrite()` — compresses the body, sets `Content-Encoding: gzip`. Skips responses <1KB, chunked responses, and non-compressible content types. Added `Response.getContent()`, `getContentType()`, `isChunked()` to support the compression logic.
- **Chunked transfer encoding**: `Response.chunked()` method enables `Transfer-Encoding: chunked`. The `build()` method formats the body as sized hex chunks terminated by `0\r\n\r\n`. Chunked responses omit `Content-Length`. Gzip is skipped for chunked responses.

`handleRead()` now also stores `Accept-Encoding` on the client session (alongside `Connection`). `processCompletedTasks()` preserves it when creating the write session.

### Connection pool validation (2026-05-30)

`PooledDataSource` rewritten for production use:

- **Sizing**: configurable `initialPoolSize` (default 5) and `maxPoolSize` (default 20). Pool grows on demand up to max.
- **Borrow with timeout**: `getConnection()` blocks up to `connectionTimeoutMs` (default 30s) when pool is exhausted, using `wait()/notifyAll()`. Throws `SQLException` on timeout.
- **Validation on borrow**: each connection is validated with `Connection.isValid(validationTimeoutSec)` (default 3s) before being handed out. Invalid connections are closed and the next idle connection is tried.
- **Idle eviction**: background daemon thread (`pool-evictor`) runs at `evictionIntervalMs` (default 1 min). Idle connections above `initialPoolSize` are trimmed. Connections exceeding `maxLifetimeMs` (default 30 min) are recycled.
- **Graceful shutdown**: `shutdown()` closes all idle and active connections, stops the eviction thread, and wakes any waiting borrowers.
- **Instance-based config**: all settings are per-instance (no more static `timeout` / `executorPoolSize` / `validSocketTimeOut`). Removed unused `executorService`.
- **Thread safety**: `synchronized` on `this` for all pool mutations, with `notifyAll()` on release/shutdown. Connection creation and closing happen outside the lock where possible.

### Proper logging (2026-05-30)

Replaced all `printStackTrace()` calls and empty catch blocks with `java.util.logging`. Every class now has a `private static final Logger logger`. Log levels used:

- **SEVERE**: Request processing failures, SSL context creation failures, connection pool failures, scheduled job failures
- **WARNING**: Dependency injection failures, service definition generation failures, WebSocket handler invocation failures, SSL keystore initialization failures, DB connection close failures, log file write failures (fallback)
- **FINE/FINER**: Expected/benign failures (reflection method resolution, stale channel cleanup, cancelled key races, missing GraphQL fields)

`Log.appendToLog()` is now `synchronized` and uses `java.util.logging` as a fallback when its own file output fails. All `InterruptedException` catches restore the interrupt flag.

### Thread safety (2026-05-30)

Audited and fixed all shared mutable state across the event loop thread, worker pool threads, and WebSocket keepalive thread. Key changes:

- **`RestHttpServer`**: `pathMapping` frozen into immutable `frozenPathMapping` at startup. `clientSessions` → `ConcurrentHashMap`. `documentationEndpoint` → `ConcurrentHashMap`. `requestFilters` → `CopyOnWriteArrayList`. Fixed `WorkerTask` to attach its session to the `SelectionKey` before waking the selector (missing `key.attach()` was causing all responses to be 500).
- **`RestActionService._CACHE_MAPS`**: `LinkedHashMap` → `ConcurrentHashMap` (read/written by all worker threads).
- **`Application._CONTEXT_MAP`**: `LinkedHashMap` → `ConcurrentHashMap`. `set()` now removes on null value (CHM rejects nulls); `get()` guards against null keys.
- **`ReflectionUtil`**: `static SimpleDateFormat` → `ThreadLocal<SimpleDateFormat>` (not thread-safe).
- **`Log`**: Double-checked locking singleton. `ThreadLocal<SimpleDateFormat>`. `appendToLog()` synchronized for safe `FileOutputStream` writes.
- **`Cache.contains()`**: Wrapped `cleanup()` + `containsKey()` in `synchronized(cacheMap)` (was calling `containsKey()` outside the lock).
- **`WebSocketSession`**: `open` → `volatile` (read by keepalive + event loop). `attributes` → `ConcurrentHashMap`.
- **`WebSocketSessionImpl`**: `closeFrameSent` → `volatile` (written in `enqueueClose()`, read in `isCloseFrameSent()` without the `outgoingFrames` lock).

### Worker-thread pool (2026-05-30)

The NIO event loop no longer processes requests inline. Endpoint dispatch (`handleRequestMapping` + `RestActionService.process()`) now runs on a configurable thread pool so slow endpoints don't block other clients.

**Architecture:**
- **`WorkerTask` inner class** — wraps `handleRequestMapping` call. Runs on a `ThreadPoolExecutor`. Stores the `Response` on `InternalClientSession`, then enqueues the `SelectionKey` into `completionQueue` and calls `selector.wakeup()`.
- **`processCompletedTasks()`** — called at the top of each event loop iteration. Drains `completionQueue`, validates each key, attaches the write session, and sets `interestOps(OP_WRITE)`.
- **`handleRead`** — after parsing the HTTP request, sets `key.interestOps(0)` to suppress further reads, creates a fresh `InternalClientSession`, and submits a `WorkerTask`. On `RejectedExecutionException` (queue full), returns `503 Service Unavailable` immediately.
- **Back-pressure** — `ThreadPoolExecutor` with bounded `LinkedBlockingQueue` and `AbortPolicy`. Default pool size: `max(4, availableProcessors * 2)`, max queue: 1000.
- **Shutdown** — `workerPool.shutdown()` + `awaitTermination(5s)` + `shutdownNow()` + final `processCompletedTasks()` drain.
- **Thread safety** — workers never touch `SocketChannel` or `SelectionKey` mutators. `ConcurrentLinkedQueue` provides happens-before between worker write and event loop read. `selector.wakeup()` is thread-safe per NIO spec.
- **Health bypass** — `GET /health` runs inline on the event loop (before `workerPool.submit()`), responding even when all workers are saturated. Pluggable health probes via `addHealthCheck()`.

### NIO event loop keep-alive and connection handling fix (2026-05-28)

Four fixes that together enable HTTP/1.1 keep-alive and fix connection handling bugs:

1. **Keep-alive support** — `handleWrite` now checks the request's `Connection` header. When the client sends `Connection: keep-alive`, the response is sent with `Connection: Keep-Alive` and the channel is re-registered for `OP_READ` instead of being disconnected. Multiple requests can reuse the same TCP connection.

2. **Removed try-with-resources on SocketChannel** — `handleWrite` previously wrapped `SocketChannel` in try-with-resources, which auto-closed the channel after every write via `Closeable`. The channel is now properly managed by `handleDisconnect` which explicitly calls `client.close()`.

3. **Non-blocking write with partial write handling** — `handleWrite` now uses a write buffer stored in `InternalClientSession`. If `channel.write()` returns 0 (socket buffer full in non-blocking mode), the method returns and waits for the next `OP_WRITE` event. Previously a `while (buffer.hasRemaining())` loop spun forever when the socket buffer was full, hanging the single-threaded event loop.

4. **Event loop key validity** — Added `continue` after each handler (`handleAccept`, `handleRead`, `handleWrite`) because `handleRead` re-registers the channel for `OP_WRITE` (cancels the old key) and `handleWrite` either re-registers for `OP_READ` or disconnects. Without `continue`, the loop called `isWritable()` on a cancelled key, throwing `CancelledKeyException`.

5. **Removed unreliable `InputStream.available()` check** — `handleRead` no longer calls `client.socket().getInputStream().available()` before reading. On non-blocking channels this call is unreliable across JDK implementations. Instead it calls `channel.read()` directly, which returns 0 when no data is available.

### WebSocket lifecycle robustness (2026-05-27)

Three fixes that together make the WebSocket layer production-ready:

1. **Broadcaster resilience** — `WebSocketBroadcaster.broadcast()` wraps `session.send()` in try-catch. A stale WebSocket session (disconnected at the TCP level but not yet removed from the active session list) can throw `CancelledKeyException` from the NIO layer. Before this fix, one stale session broke the entire broadcast loop. Now the broadcaster catches the exception per-session, unregisters the stale session, and continues broadcasting to all remaining healthy sessions.

2. **IOException cleanup** — `RestHttpServer.handleRead()` now catches `IOException` (and detects `read == -1`) in the WebSocket path. Previously an IOException from a dropped client was swallowed by the outer catch and the session was never cleaned up. Now it calls `handleDisconnect` immediately — session unregistered from broadcaster, key cancelled, channel closed.

3. **Keepalive ping/pong** — A daemon thread (`ws-keepalive`) sends OP_PING every 30 seconds to all WebSocket connections and closes any session that hasn't responded with pong within 10 seconds. This also sweeps for stale sessions where `isOpen() == false` and removes them from the broadcaster. Without this, proxies and firewalls can silently drop idle connections.

### Root path routing and request filter fixes (2026-08-02)

Four fixes that together make `@Action(path = "/")` and `RequestFilter` work correctly for the root path:

1. **`@Action(path = "/")` now matches `GET /`** — `handleRequestMapping` tries endpoint matching **before** the `index.htm`/`index.html` fallback rewrite. When `contextPath` is empty (request to `/`), it also tries matching against `"/"` because `registerEndpoint` stores the root endpoint with key `"/"` (from `base + ""`). Previously the empty context path was rewritten to `/index.htm` before endpoint lookup, so root endpoints never matched.

2. **`RequestFilter` runs for ALL requests** — filters are now invoked for every request, even those without a matching `@Action` endpoint. `EndpointInfo` is `null` when no endpoint matches, allowing filters to handle arbitrary paths (e.g. custom routing, auth gates). Previously `applyRequestFilters` was only called inside the `if (restAction != null)` branch, so unmatched paths bypassed all filters and went straight to 404.

3. **Endpoints have priority over static file serving** — endpoint matching happens before the static document fallback. An `@Action(path = "/")` endpoint now wins over `web/index.html`. Previously the empty-path → `index.htm` rewrite happened first, so static files always shadowed root endpoints.

4. **Directory paths no longer break endpoint matching** — `isRequestADocument()` now checks `checkFile.isFile()` in addition to `checkFile.exists()`. Previously a directory (e.g. `web/app/`) returned `true` for `exists()`, causing `Files.readAllBytes()` to throw `IOException: Is a directory` (500) instead of falling through to endpoint matching.

**Test coverage:** `RestHttpServerRootPathTest` (6 tests) covers all four fixes plus the rate limiter regression test below.

### Rate limiter null-endpoint crash on static files (2026-08-02)

`RateLimitModuleService.apply()` called `endpoint.getAnnotation()` without checking for null. After the root path fix made `EndpointInfo` null for unmatched paths (including static files like `/css/app.css`), the rate limiter threw `NullPointerException` on every static file request.

**Fix:** Added `if (endpoint == null) return null;` at the top of `apply()` — static files and other unmatched paths skip rate limiting entirely.

## Production Readiness

The project goal is moving from hobby project to production use — the author is using it to serve paying customer software. All changes and suggestions below must be evaluated through this lens.

### Known architecture limitations

All critical production issues from the roadmap have been addressed. Remaining areas for future work:

- **Single-threaded NIO event loop** — the selector loop is single-threaded by design. Under extreme connection volume (10K+ concurrent), `selector.select()` and key iteration can become a bottleneck. A multi-selector architecture (one accept selector + N read/write selectors) would scale further.
- **No built-in TLS** — `SSLContextManager` can create an `SSLContext` but it's not wired into the accept loop. TLS termination currently requires a reverse proxy (nginx, haproxy). Could be added as an `SSLEngine` wrapper around `SocketChannel`.
- **Chunked encoding is buffered** — `Response.chunked()` works but the full response body is built in memory before sending. True streaming (incremental chunk writes) would require changes to the `Response` → `handleWrite` pipeline.
- **No HTTP/2** — HTTP/1.1 only. HTTP/2 requires a binary framing layer, multiplexed streams, and header compression (HPACK). This is a major undertaking but would bring significant performance benefits for many-small-request workloads.
- **No WebSocket permessage-deflate** — the WebSocket implementation doesn't support the compression extension. Most production WebSocket deployments use it.
- **`pathMapping` is instance-based** — each `RestHttpServer` instance now has its own routing table (changed from static to instance field 2026-05-30). Multiple servers with different endpoints can coexist in the same JVM.

### Production roadmap (see README.md for full checklist)

The prioritized path to production, in order:

1. ~~**Worker-thread pool**~~ — done 2026-05-30. `handleRequestMapping` + `restAction.process()` now run on a configurable `ThreadPoolExecutor`.
2. ~~**Thread safety**~~ — done 2026-05-30. Routing table frozen at startup via `freezePathMapping()`. `ConcurrentHashMap` for `clientSessions`, `documentationEndpoint`, `Application._CONTEXT_MAP`, `RestActionService._CACHE_MAPS`. `CopyOnWriteArrayList` for `requestFilters`. `ThreadLocal<SimpleDateFormat>` in `ReflectionUtil` and `Log`. `volatile` for `WebSocketSession.open` and `WebSocketSessionImpl.closeFrameSent`. Synchronized `Log.appendToLog()` and `Cache.contains()`.
3. ~~**Proper logging**~~ — done 2026-05-30. All 11 `printStackTrace()` calls replaced with `logger.log(Level, msg, exception)`. All 12 empty/silent catch blocks replaced with proper log statements at appropriate levels. `Log` class uses `java.util.logging` as fallback for its own internal failures.
4. ~~**Connection pool validation**~~ — done 2026-05-30. `PooledDataSource` rewritten with production defaults: configurable min/max pool size (default 5/20), blocking borrow with 30s timeout, per-borrow connection validation with `isValid()`, background idle eviction thread, max connection lifetime enforcement, graceful shutdown. Static configuration fields moved to per-instance. Removed unused `executorService`.
5. ~~**HTTP/1.1 compliance**~~ — done 2026-05-30. Host header validation (400 if missing per RFC 7230 §5.4), gzip response compression (opt-in via `Accept-Encoding: gzip`, skips small <1KB responses and non-text content types), chunked transfer encoding (`Response.chunked()`), `Content-Encoding: gzip` and `Transfer-Encoding: chunked` headers.
6. ~~**Rate limiting**~~ — done 2026-05-30. Rate limiting is opt-in with two-level configuration: (1) global defaults via `rateLimitModule.setEnabled(true)` + `setDefaultRequests()`/`setDefaultPerSeconds()`, (2) per-endpoint overrides via `@RateLimit(requests=N, perSeconds=S)` annotation. Use `@RateLimit(requests=0)` to whitelist an endpoint. Background cleanup thread prevents memory leaks. Fully backwards-compatible — no annotation, no rate limit.
7. ~~**Graceful shutdown**~~ — done 2026-05-30. Six-phase shutdown: (1) stop accepting, (2) drain worker pool with configurable timeout, (3) process remaining completed tasks, (4) drain pending writes with short select loop, (5) force-close remaining connections after timeout, (6) close selector and server socket. During drain, `Connection: keep-alive` is suppressed so clients know to disconnect. Configurable via `setDrainTimeoutMs()` (default 10s).
8. ~~**Health-check endpoint**~~ — done 2026-05-30. `GET /health` runs inline on the NIO event loop, bypassing the worker pool entirely (responds even when all workers are saturated). Returns JSON: `{"status":"UP","database":"UP","disk":"UP","uptime":"5m"}` (200 OK) or `{"status":"DOWN",...,"redis":"Connection refused"}` (503). Pluggable via `addHealthCheck(HealthCheck)`. Configurable path via `setHealthPath()`. Built-in uptime always reported.
9. ~~**Request timeout**~~ — done 2026-05-30. Two-level timeout: (1) global `defaultRequestTimeoutMs` (default 30s) checked in `WorkerTask.run()` before processing — if the request spent too long in the queue or the endpoint took too long, returns `408 Request Timeout`. (2) per-endpoint `@Timeout(ms = N)` annotation overrides the global timeout — use `ms = 0` to disable timeout for long-running endpoints (CSV exports, streams). (3) idle read timeout: `sweepIdleConnections()` runs every 30s in the event loop, closing connections that haven't sent data within `idleReadTimeoutMs` (default 60s). Added `Response.RequestTimeout()` and 408 status message.

### Guidelines for production work

- When adding worker threads, keep the NIO event loop single-threaded — only offload the CPU/IO-bound processing, not the socket I/O.
- The routing table (`pathMapping`) is populated at startup via `addEndpoint()`. After `startServer()` is called, it should be treated as read-only. If hot-reload is needed later, use copy-on-write.
- `InternalClientSession` is the attachment on every `SelectionKey`. It must remain the bridge between the event loop and worker threads — workers should not touch `SocketChannel` directly.
- Tests for thread-safety issues must run under load (multiple threads, thousands of iterations). A single-run test won't catch race conditions.
- The `/health` endpoint should bypass the worker pool entirely — it must respond even when all workers are saturated.
- `cintix-html-engine` is compiled with `--release 8` for Java 8+ portability. Any changes to that project must preserve the release-8 target.

