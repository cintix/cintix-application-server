# Cintix Application Server

A lightweight, annotation-driven Java 8+ REST application server built on `java.nio` — no servlet container, no external framework. Production-hardened with a worker-thread pool, connection pooling, graceful shutdown, health checks, and pluggable rate limiting.

**~100 KB core jar.** Zero XML configuration. Batteries included. **LGPL-3.0 licensed.**

## Quick Start

```java
RestHttpServer server = new RestHttpServer() {};
server.addEndpoint("/api", new MyEndpoints());
server.bind(new InetSocketAddress(8080));
server.startServer();
```

```java
public class MyEndpoints {
    @GET
    @Action(path = "/hello")
    public Response hello() {
        return new Response().OK().ContentType("application/json")
            .data("{\"message\":\"Hello, world!\"}");
    }

    @GET
    @Action(path = "/users/:id")
    public Response getUser(String id) {
        return new Response().OK().ContentType("application/json")
            .model(findUser(id));
    }
}
```

## Features

### Core HTTP

| Feature | Description |
|---------|-------------|
| **Annotation-driven routing** | `@Action(path)`, `@GET`/`@POST`/`@PUT`/`@DELETE` — exact and regex/parameterized paths (`:param`) |
| **HTTP/1.1 compliance** | Host header validation (RFC 7230), gzip compression, chunked transfer encoding, keep-alive |
| **Static file serving** | HTML, CSS, JS, images from `DOCUMENT_ROOT` with directory traversal protection |
| **Server-page rendering** | `.html`/`.htm` files processed through `cintix-html-engine` with request parameter merging |
| **JSON & text generators** | Built-in `application/json` and `text/plain` model generators — pluggable via `registerModelGenerator()` |
| **Response caching** | `@Cache`, `@Static`, `@CacheByStatus` annotations with in-memory TTL-based caching |

### Production

| Feature | Description |
|---------|-------------|
| **Worker-thread pool** | Configurable `ThreadPoolExecutor` — slow endpoints don't block other clients. Back-pressure: `503 Service Unavailable` when queue is full |
| **Connection pooling** | `PooledDataSource` with dynamic sizing (default 5→20), borrow validation, idle eviction, max lifetime |
| **Graceful shutdown** | 6-phase drain: stop accept → drain workers → flush writes → close connections → release resources |
| **Health checks** | `GET /health` bypasses worker pool. Pluggable probes. Returns `200` (UP) or `503` (DOWN) with JSON status |
| **Rate limiting** | Opt-in, two-level: global defaults + `@RateLimit` per-endpoint annotation. `requests=0` whitelists an endpoint |
| **Request timeout** | Global default (30s) + `@Timeout(ms)` per-endpoint annotation. Idle read sweep closes hung connections |
| **Gzip compression** | Automatic when client sends `Accept-Encoding: gzip`. Skips small (<1KB) and non-text responses |

### WebSocket

| Feature | Description |
|---------|-------------|
| **Annotation lifecycle** | `@WebSocket`, `@OnOpen`, `@OnMessage`, `@OnBinary`, `@OnClose`, `@OnError` |
| **RFC 6455** | Full frame handling — text, binary, ping/pong, close with status codes |
| **Built-in broadcaster** | Fan-out to all sessions on a path. Resilient to stale sessions |
| **Keepalive** | Server-initiated ping every 30s, closes unresponsive connections after 10s |
| **Query strings** | Upgrade query params copied to session attributes (`qs.token`, `qs.room`) |

### Plugins

| Plugin | Description |
|--------|-------------|
| **GraphQL** | Lexer, parser, AST, executor. `@Query`/`@Mutation` annotations. Register via `GraphQLModule` |
| **Rate Limiting** | `@RateLimit(requests=N, perSeconds=S)`. Per-client key via header (default: `X-Forwarded-For`) |
| **Scheduler** | `@Scheduled(fixedRate, initialDelay)` on methods. `ScheduledExecutorService`-backed |

### Database

| Feature | Description |
|---------|-------------|
| **ORM** | `EntityManager` with annotation-based mapping |
| **Connection pooling** | `PooledDataSource` — production defaults, borrow validation, background eviction |
| **Transactions** | `TransactionableConnection` with savepoints and auto-rollback on error |

### Security

| Feature | Description |
|---------|-------------|
| **SSL/TLS** | `SSLContextManager` creates TLS context from JKS keystore |
| **Certificate management** | `SSLCertificateManager` loads signed certificates |

## Configuration

All configuration is done in code — no XML, no properties files.

```java
RestHttpServer server = new RestHttpServer() {};

// Server
server.setDocumentRoot("public");
server.bind(new InetSocketAddress(8080));

// Worker pool
server.setWorkerThreads(8);
server.setMaxQueueSize(500);

// Timeouts
server.setDefaultRequestTimeoutMs(30_000);   // 30 seconds
server.setIdleReadTimeoutMs(60_000);         // 60 seconds
server.setDrainTimeoutMs(10_000);            // graceful shutdown drain

// Health
server.setHealthPath("/.well-known/health");
server.addHealthCheck(new DatabaseHealthCheck());

// Events
server.setConnectionEvents(new HttpConnectionEvents() { ... });
server.setRequestEvents(new HttpRequestEvents() { ... });
```

### Rate limiting (opt-in)

```java
RateLimitModuleService rateLimit = new RateLimitModuleService();
rateLimit.setEnabled(true);                       // off by default
rateLimit.setDefaultRequests(200);                // per 60s window
rateLimit.setDefaultPerSeconds(60);
ModuleRegistry.initialize(server, rateLimit);

// Per-endpoint overrides:
@RateLimit(requests = 10, perSeconds = 60)        // strict
@RateLimit(requests = 500, perSeconds = 60)       // generous
@RateLimit(requests = 0, perSeconds = 1)          // whitelist
```

### Request timeout (per-endpoint)

```java
@Timeout(ms = 120_000)   // 2 minutes for slow exports
@Timeout(ms = 0)          // no timeout (streaming)
// No annotation → uses global default (30s)
```

## Build & Run

```bash
ant clean compile jar              # builds dist/cintix-application-server.jar
ant compile-test                   # compiles tests
java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.AllTests
```

**Requirements:** Java 8+, Apache Ant. Source/target compiled with `--release 8` for portability across JDK 8–25+.

## Project Layout

```
src/dk/cintix/application/server/
  modules/
    http/server/          REST routing, NIO event loop, request parsing, WebSocket
      endpoint/           RestHttpServer, RestHttpRequest, HealthCheck, WebSocketFrame
      services/           RestActionService, WebSocketService, Response, JSON generators
    graphql/              GraphQL plugin — lexer, parser, AST, executor, endpoint adapter
    ratelimit/            Rate limit plugin — @RateLimit annotation, request filter
    scheduler/            Scheduler plugin — fixed-rate job execution
    database/             PooledDataSource, EntityManager, TransactionableConnection
    security/             SSL context and certificate management
  infrastructure/         Cache, ReflectionUtil, Application, Log, annotations, Plugin system
```

## Plugin Architecture

Plugins implement `Plugin` and are wired through `ModuleRegistry`. They register with `PluginContext` and can add request filters, WebSocket handlers, or custom modules.

**Built-in plugins** (discovered via `ServiceLoader`): GraphQL, Rate Limiting, Scheduler

**Creating a plugin:**

```java
public class MyPlugin implements Plugin {
    public String getName() { return "my-plugin"; }

    public void register(PluginContext context) {
        context.registerModule(MyModule.class, myModuleInstance);
        context.getHttpModule().addRequestFilter((request, endpoint) -> {
            // custom filter logic
            return null;  // null = pass through
        });
    }
}
```

Register via `META-INF/services/dk.cintix.application.server.infrastructure.modules.Plugin` for auto-discovery, or directly:

```java
PluginContext context = ModuleRegistry.initialize(server, new MyPlugin());
```

## Production Readiness

All items on the production roadmap are complete as of 2026-05-30:

- [x] Worker-thread pool with back-pressure
- [x] Thread safety — immutable routing, concurrent collections, volatile fields
- [x] Proper logging — `java.util.logging` throughout, zero swallowed exceptions
- [x] Connection pool validation — borrow checks, idle eviction, max lifetime
- [x] HTTP/1.1 compliance — Host header, gzip, chunked encoding
- [x] Rate limiting — opt-in, two-level (global + per-endpoint)
- [x] Graceful shutdown — 6-phase drain with configurable timeout
- [x] Health-check endpoint — bypasses worker pool, pluggable probes
- [x] Request timeout — global + `@Timeout` annotation, idle read sweep

## Future Roadmap

These are "next level" improvements — the server is production-ready without them:

| Area | Description |
|------|-------------|
| **TLS in event loop** | `SSLContextManager` exists but isn't wired into the accept loop. Currently needs a reverse proxy for TLS. |
| **Multi-selector** | Single-threaded event loop handles 10K+ connections comfortably. A multi-selector architecture would scale further. |
| **HTTP/2** | Binary framing, multiplexed streams, HPACK. Significant throughput improvement for many-small-request workloads. |
| **Streaming chunked encoding** | Current `Response.chunked()` buffers full body. True streaming would enable incremental writes. |
| **WebSocket permessage-deflate** | Compression extension for WebSocket frames. |
| **CORS plugin** | `@CrossOrigin` annotation, header injection as a plugin. |
| **Auth plugin** | `@Authenticated`, JWT validation, OAuth2 client. |
| **Metrics** | Prometheus `/metrics` endpoint — request counts, latency histograms, active connections. |
| **OpenAPI** | Generate OpenAPI 3.0 spec from annotations (extends the built-in `?jsd` JSON service descriptor). |
| **Multipart upload** | `@Upload` annotation, stream files to disk. |
| **Redis caching** | `@Cache` backed by Redis instead of in-memory. |
| **OpenAPI & MCP** | Built-in. `server.enableOpenApi(title, version)` and `server.enableMcp(toolHandlers...)` — see below. |

## OpenAPI & MCP (built-in)

### OpenAPI Documentation

Enable after registering endpoints:

```java
server.addEndpoint("/api", new UserEndpoint(), new ProjectEndpoint());
server.enableOpenApi("My API", "1.0.0");
```

Serves `GET /api/openapi.json` (OpenAPI 3.0.3 spec) and `GET /api/docs` (Swagger UI).

**`@ApiDoc`** on `@Action` methods: `summary`, `description`, `tag`, `deprecated`.
**`@ApiTag`** on endpoint classes: sets default `name` and `description` for all methods in that class.
Without annotations, summaries are auto-generated from method names and tags from path prefixes.

### MCP (Model Context Protocol)

```java
server.enableMcp(new AdminTools());
```

Registers `POST /api/mcp` (JSON-RPC 2.0, MCP `2024-11-05`). Auth via existing `addRequestFilter()`.

**`@McpTool`** marks a method as an MCP tool. **`@McpParam`** on parameters for name/description/required. Java types auto-map to JSON Schema: `String→"string"`, `int/Integer→"integer"`, `boolean/Boolean→"boolean"`, `Map→"object"`, `List→"array"`.

Any `@McpTool` method on an endpoint class registered via `addEndpoint()` is auto-discovered — no separate registration needed.
