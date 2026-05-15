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
- `src/dk/cintix/application/server/rest` REST action dispatch and endpoint model
- `src/dk/cintix/application/server/rest/http` HTTP server loop, request parsing, sessions
- `src/dk/cintix/application/server/jdbc` datasource, pooling, transaction helpers
- `src/dk/cintix/application/server/ssl` SSL context and certificate loading
- `src/dk/cintix/application/server/io` shared utilities (cache, memory stream, reflection)
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
java -cp build/classes:build/test/classes:lib/* dk.cintix.application.server.AllTests
```

## SSL Notes
- SSL uses `.keystore` (JKS) in the project working directory.
- `SSLContextManager` builds a `TLS` context from the keystore key/password.
- Ensure `.keystore` exists and matches the provided key password.

## Features added since v1

- **WebSocket** — annotation-driven (`@WebSocket`, `@OnOpen`, `@OnMessage`, `@OnBinary`, `@OnClose`, `@OnError`), RFC 6455 frame handling, built-in broadcaster for fan-out to all sessions on a path.
- **GraphQL** — query and mutation engine with built-in lexer/parser/executor. Register via `addGraphQLEndpoint(path, handler)` with `@Query`/`@Mutation` annotations.

## Future Plugin System

A plugin architecture was considered to keep the server lightweight — load only what you need.

**Decision:** not yet. Domain-specific modules (GraphQL, JDBC) are already cleanly separated. Not calling `addGraphQLEndpoint` means GraphQL costs nothing. A plugin system will be implemented when jar size, independent versioning, or third-party contributions justify the added complexity.

The planned design: a `Plugin` interface loaded via `ServiceLoader`. Drop a jar in `lib/` with the service descriptor, done. Core (REST, WebSocket, SSL) stays built-in. Domain modules (GraphQL, JDBC) become plugins.

## Current Status
The codebase has been recently reviewed and hardened in request parsing, cache correctness, server-page parameter merging, session id uniqueness, SSL keystore loading, and JDBC transaction/pool stability.
