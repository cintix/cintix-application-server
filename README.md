# cintix-application-server

Cintix Application Server is a lightweight Java 8 REST application server with built-in endpoint mapping, static file serving, simple server-page rendering, JDBC helpers, in-memory caching, and optional SSL context support.

## Features
- Annotation-driven REST actions (`@Action`, `@GET/@POST/@PUT/@DELETE` flow)
- Static document serving from `DOCUMENT_ROOT`
- HTML server-page rendering via `cintix-html-engine`
- Request/response pipeline with cache strategies (`STATIC`, `DYNAMIC`)
- JDBC utilities (`EntityManager`, `TransactionableConnection`, `PooledDataSource`)
- SSL keystore loading and TLS context creation

## Project Layout
- `src/dk/cintix/application/server/modules/http/server` REST routing, HTTP server loop, request parsing, sessions, WebSocket support
- `src/dk/cintix/application/server/modules/graphql` GraphQL plugin contract, endpoint adapter, parser, executor, registry
- `src/dk/cintix/application/server/modules/ratelimit` rate limit plugin and request filter
- `src/dk/cintix/application/server/modules/scheduler` scheduler plugin and fixed-rate job support
- `src/dk/cintix/application/server/modules/database` datasource, pooling, transaction helpers
- `src/dk/cintix/application/server/modules/security` SSL context and certificate loading
- `src/dk/cintix/application/server/infrastructure` shared utilities, annotations, plugin contracts, module registry
- `lib/` third-party jars (Gson, PostgreSQL driver, HTML engine)
- `test/` regression tests (happy/unhappy paths)

## Build & Run
From repository root:

```bash
ant clean
ant compile
ant jar
ant run
```

Useful output paths:
- `build/classes` compiled classes
- `dist/cintix-application-server.jar` packaged jar

## Testing
`ant test` compiles test sources but does not currently execute the custom test suite in this project setup.

Run the full regression suite with:

```bash
ant compile-test
java -cp 'build/classes:build/test/classes:lib/*' dk.cintix.application.server.AllTests
```

## SSL Notes
- SSL uses `.keystore` (JKS) in the project working directory.
- `SSLContextManager` builds a `TLS` context from the keystore key/password.
- Ensure `.keystore` exists and matches the provided key password.

## Features added since v1

- **WebSocket** — annotation-driven (`@WebSocket`, `@OnOpen`, `@OnMessage`, `@OnBinary`, `@OnClose`, `@OnError`), RFC 6455 frame handling, built-in broadcaster for fan-out to all sessions on a path.
- **GraphQL plugin** — query and mutation engine with built-in lexer/parser/executor. Register the plugin, then call `graphql.addEndpoint(path, service)` with `@GraphQLModule.Query`/`@GraphQLModule.Mutation` annotations.

## Plugin System

The server has a lightweight plugin architecture to keep core HTTP/REST small while allowing optional capabilities.

Plugins implement `dk.cintix.application.server.infrastructure.modules.Plugin` and are wired through `ModuleRegistry`. They can be registered directly or discovered with `ServiceLoader` via `META-INF/services/dk.cintix.application.server.infrastructure.modules.Plugin`.

Core (REST, WebSocket, SSL, static files) stays built-in. GraphQL, rate limiting, and scheduling are plugins. JDBC/database remains built in for now and is a future extraction candidate.

Example:
```java
PluginContext context = ModuleRegistry.initialize(server, new GraphQLModuleService());
GraphQLModule graphql = context.getModule(GraphQLModule.class);
graphql.addEndpoint("/graphql", new UserQueries(), new ProductQueries(), new OrderMutations());
```

GraphQL service classes use annotations from the module contract:
```java
public class UserQueries {
    @GraphQLModule.Query("user")
    public User user(String id) {
        return findUser(id);
    }
}

public class OrderMutations {
    @GraphQLModule.Mutation("createOrder")
    public Order createOrder(String productId) {
        return createOrderFor(productId);
    }
}
```

One GraphQL endpoint can register one or more service classes. Core HTTP no longer exposes `addGraphQLEndpoint`; use the `GraphQLModule` contract instead.

### Additional potential future plugins

These follow the annotation-driven pattern already established. Listed roughly by production priority:

| Plugin | Purpose |
|--------|---------|
| **Auth** | `@Authenticated` annotation, JWT validation, OAuth2 client. Request interceptor before `@Action` methods. |
| **Health checks** | `@HealthCheck` annotation, aggregated `/health` endpoint. DB, disk, memory probes. |
| **CORS** | `@CrossOrigin` annotation on endpoints. Simple header injection. |
| **Metrics** | Prometheus `/metrics` endpoint. Request counts, response times, active connections. |
| **OpenAPI** | Like the built-in `?jsd` generator but producing OpenAPI 3.0 spec. `@ApiDoc` annotations for descriptions. |
| **SSE** | `@SSE(path)` annotation for Server-Sent Events. Simpler than WebSocket for one-way streaming. |
| **Multipart upload** | `@Upload` annotation, stream files to disk. |
| **Redis** | Connection pool + `@Cache` backed by Redis instead of in-memory.

## Current Status
The codebase has been recently reviewed and hardened in request parsing, cache correctness, server-page parameter merging, session id uniqueness, SSL keystore loading, and JDBC transaction/pool stability.
