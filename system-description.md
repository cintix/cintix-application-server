# System Description of Cintix Application Server

This document describes how the program works from start to finish, written so a new programmer can understand it without having seen the code first.

---

## 1. What does the program do?

Cintix Application Server is a **library** (not a standalone program). It provides the building blocks for creating a web server in Java without needing big frameworks like Spring or Jakarta EE. Other programs include this library and extend its classes to create their own web applications.

The library provides:

- An HTTP server that listens for web requests and sends back responses
- A system for mapping URLs to Java code using simple annotations (markers in the code)
- A database connection pool for communicating with databases
- A mini-ORM (Object-Relational Mapper) that helps turn database rows into Java objects
- An in-memory cache for storing responses so they do not need to be recomputed
- A file-based logging system
- A client for making HTTP requests to other servers
- SSL/TLS support for secure connections

Everything runs inside a single Java process. The library depends on only three external JAR files: Gson (for JSON), PostgreSQL JDBC driver (for database connections), and the cintix-html-engine (for server-side HTML rendering).

---

## 2. Program startup

### 2.1 `Main.java` - The entry point

The file `src/dk/cintix/application/server/Main.java` is the program's entry point. It is minimal:

```java
public static void main(String[] args) {
    ModuleRegistry.initialize();
    System.out.println("Cintix Application Server is a library module...");
}
```

Unlike the Pipeline Engine, this Main class does **not** start a web server. It only initializes the module registry and prints a message. The server is designed to be extended by other programs. To use it, you create a subclass of `RestHttpServer` and call its `start()` method from your own application.

### 2.2 `ModuleRegistry.java` - The wiring point

The file `infrastructure/modules/ModuleRegistry.java` contains a static `initialize()` method that sets an `APP_INITIALIZED` flag in the application context. In the base library, it is mostly a placeholder. When other projects use this library, they extend this class to wire together their own modules (as the Pipeline Engine does with its own `ModuleRegistry`).

### 2.3 `Application.java` - Global settings holder

The file `infrastructure/Application.java` is a static key-value store (`LinkedHashMap<String, String>`) available to the entire program. It can:

- Store and retrieve values by name using `set(key, value)` and `get(key)`
- Return the program's working directory via `getPath()`
- Create and return a `./conf/` folder for configuration files via `getConfigFolder()`
- Create and return a `./cache/` folder for temporary files via `getCacheFolder()`

The `RestHttpServer` uses this to store the `DOCUMENT_ROOT` value (the folder where HTML files are located).

---

## 3. The HTTP server - the heart of the library

### 3.1 `RestHttpServer.java` - The main server class

The file `modules/http/server/endpoint/RestHttpServer.java` is the core of the entire library. It is an **abstract class** implementing the `HttpModule` interface. This means other projects must create a subclass of it to build an actual server.

The server uses **Java NIO** (New Input/Output), which is a non-blocking I/O API. Instead of creating one thread per connection (which uses a lot of memory), it uses a single thread with a **selector** that monitors many connections at once. Think of it like a receptionist who can handle many phone calls by quickly switching between them rather than hiring one receptionist per call.

### 3.2 The NIO event loop

`startServer()` runs an infinite loop that looks like this:

```
while (server is running) {
    selector.select();               // wait for events on any channel
    for (each ready key) {
        if (key is acceptable)  → handleAccept();    // new connection
        if (key is readable)    → handleRead();      // data arrived
        if (key is writable)    → handleWrite();     // ready to send data
    }
    remove disconnected keys;
    noop();  // tiny sleep to prevent CPU spinning
}
```

This loop runs on the main thread. All request handling - including database queries - happens on this same thread. This means that slow operations will block the entire server, so it is best suited for quick operations.

### 3.3 The lifecycle of a single request

This is the most important concept to understand. Every web request goes through four stages:

#### Stage 1: Accept (handleAccept)

When a browser connects to the server:

1. The `ServerSocketChannel` accepts the new connection, producing a `SocketChannel`
2. A `RestClient` object is created with a unique session ID. The format is `SS-<timestamp>-<sequence>`, for example `SS-1715467890-42`. Each connection gets a monotonically increasing sequence number.
3. The session is stored in a map so it can be found later
4. The channel is registered with the selector for `OP_READ` (meaning "tell me when there is data to read")

#### Stage 2: Read and parse (handleRead)

When the browser sends its HTTP request:

1. Up to 5 megabytes of raw bytes are read from the socket into a buffer
2. `parseRequest()` converts these raw bytes into a `RestHttpRequest` object. The parsing process:
   - Splits the data into lines
   - Reads the first line to extract the HTTP method (GET, POST, PUT, DELETE) and the path
   - Calls `HttpUtil.parseQueryStrings()` to separate the path from query parameters (the part after `?` in a URL)
   - Calls `HttpUtil.parseHeaderKeys()` to read HTTP headers (like `Content-Type`, `Cookie`, etc.)
   - Calls `HttpUtil.parsePostFields()` to read any POST body data (form submissions)
3. `handleRequestMapping()` is called to determine what to do with the request

#### Stage 3: Route and invoke (handleRequestMapping)

This is where the server decides what to send back. The routing works in this order:

**Step 1: Check for JSD documentation request**

If the URL has `?jsd` appended to it, the server returns JSON documentation about the endpoint (JSON Service Description). This is automatically generated by `JsonServiceDescriptionEngine` which inspects the annotations on the endpoint class.

**Step 2: Check for a static file**

If the path matches a file in the `DOCUMENT_ROOT` folder:

- **`.html` or `.htm` files** are processed through the cintix-html-engine. This is a server-side templating system. The HTML file can contain custom tags that get replaced with dynamic content. POST parameters, query string parameters, and template variables (prefixed with `@`) are all merged and passed to the template engine. Query parameters override POST parameters when the same key appears in both.

- **Other files** (CSS, JavaScript, images, etc.) are served directly. The server determines the correct MIME type (like `text/css` for CSS files or `image/png` for PNG images) using the `MimeTypes` class, which knows about roughly 80 file extensions. Unknown extensions default to `application/octet-stream`.

- **Directory traversal protection**: The server checks that the resolved file path stays within the document root folder. This is called a "jail check" and prevents attackers from reading files outside the web folder by using paths like `../../../etc/passwd`.

**Step 3: Check REST endpoints**

If the path does not match a static file, the server looks for a matching endpoint:

1. First, `locateEndpoint()` tries to find an exact match in the HTTP-method-specific map. The server maintains separate maps for GET, POST, PUT, and DELETE requests.

2. If no exact match is found, it tries regex pattern matching. When an endpoint is registered with a path like `/project/{id}`, `HttpUtil.complieRegexFromPath()` converts `{id}` into a regular expression capture group `(\S+)`, so the path becomes the regex `^(/project/)(\S+)$`. Patterns are sorted longest-first so more specific paths are tried before general ones.

3. If still no match is found, the server returns a 404 Not Found response.

**Step 4: Invoke the matching method**

When a match is found, the server calls `RestActionService.process()`. This is the endpoint invocation engine and is described in detail in section 4.

#### Stage 4: Write and disconnect (handleWrite, handleDisconnect)

After the endpoint method returns a `Response` object:

1. The response is attached to the `InternalClientSession`
2. The channel is registered for `OP_WRITE`
3. `handleWrite()` calls `response.build()`, which serializes the entire HTTP response to raw bytes (status line + headers + blank line + body)
4. The bytes are written to the socket
5. The session is removed from the map
6. The channel is deregistered and the key is cancelled

### 3.4 The Response object

The `Response` class (`modules/http/server/services/domain/models/Response.java`) uses a fluent builder pattern. This means you chain method calls together to build the response step by step.

**Status code methods:**
- `OK()` → HTTP 200
- `Created()` → HTTP 201
- `NoContent()` → HTTP 204
- `MovedPermanently()` → HTTP 301 (sets a Location header)
- `MovedTemporary()` → HTTP 302 (sets a Location header)
- `BadRequest()` → HTTP 400
- `Unauthorized()` → HTTP 401
- `Forbidden()` → HTTP 403
- `NotFound()` → HTTP 404
- `InternalServerError()` → HTTP 500
- `BadGateway()` → HTTP 502
- `ServiceUnavailable()` → HTTP 503

**Content methods:**
- `model(object)` — serializes the object to JSON (or another format) using a ModelGenerator. The format is chosen based on the Content-Type header.
- `data(string)` — sends raw text
- `Content(byte[])` — sends raw bytes
- `document(request, "filename.html")` — renders an HTML template through the cintix-html-engine

**Other methods:**
- `header(key, value)` — adds a custom HTTP header
- `Location(uri)` — sets the Location header (used for redirects)
- `variable(name, value)` — adds a variable for the template engine (prefixed with `@` internally)
- `ContentType(type)` — sets the Content-Type header

**Example of building a response:**
```java
return new Response()
    .OK()
    .ContentType("application/json")
    .model(myDataObject);
```

**The build() method** - When the response needs to be sent over the network, `build()` assembles the raw HTTP response:
1. Status line: `HTTP/1.1 200 OK\r\n`
2. Date header: current server time
3. Server header: `Cintix-Application-Server(CAS)/1.5`
4. Custom headers from `header()` calls
5. Content-Type header with charset (`utf-8` for text types)
6. `Connection: Closed\r\n` (the server closes connections after each response)
7. Content-Length header
8. Blank line (`\r\n`)
9. Body bytes

### 3.5 `CachedResponse`

The `CachedResponse` class extends `Response`. It overrides `build()` to return pre-serialized bytes that were stored in the cache. This avoids re-serializing the same response data on every request.

### 3.6 `HttpUtil.java` - The request parser

The file `modules/http/server/endpoint/HttpUtil.java` contains static helper methods that convert raw HTTP text into structured data:

- **`parseQueryStrings(contextPath, queryStrings)`** — splits a URL like `/projects?name=test&page=2` into the path `/projects` and a map of query parameters `{name: "test", page: "2"}`. Handles empty values (keys with no value after the `=` sign).

- **`parseHeaderKeys(requestLines, headers, linesProcessed)`** — reads HTTP headers from the request lines. Splits on the first `:` (using limit 2), which is important because some header values contain colons (like `Content-Type: application/json; charset=utf-8`).

- **`parsePostFields(linesProcessed, requestLines, postFields)`** — parses URL-encoded POST data from the body of the request. Also stores the raw unparsed POST body under the key `!RAW`.

- **`contentTypeMatch(accept, contentType)`** — matches a request's `Accept` header against a content type, converting `*` wildcards to regex patterns.

- **`complieRegexFromPath(String path)`** — converts a path pattern like `/project/{id}/task/{taskId}` into a regex like `^(/project/)(\S+)(/task/)(\S+)$`. The values matching `{id}` and `{taskId}` are later extracted and passed as arguments to the endpoint method.

### 3.7 `MimeTypes.java` - File type detection

The `ContentType(String ext)` method maps file extensions to MIME types. It knows about roughly 80 types: `.html` → `text/html`, `.css` → `text/css`, `.js` → `application/javascript`, `.json` → `application/json`, `.png` → `image/png`, and many more. Unknown extensions return `application/octet-stream`.

### 3.8 `InternalClientSession.java` - Per-connection state

This is a small class attached to each NIO `SelectionKey`. It holds:
- `sessionId` — the unique session identifier
- `response` — the `Response` object that the read handler builds and the write handler sends
- `keys` — a `Map<String, Object>` for arbitrary session-scoped data

---

## 4. How endpoints and annotations work

### 4.1 Registering endpoints

When a program calls `addEndpoint("/api/projects", new ProjectApiEndpoint())`, the server scans the endpoint object's class for methods that have routing annotations. This is done using Java reflection — the ability of a program to inspect its own classes and methods at runtime.

For each method in the class, the server checks:
- Does it have an `@Action` annotation? This gives the sub-path.
- Does it have `@POST`, `@PUT`, or `@DELETE`? If not, it defaults to GET.
- The full URL path is built by combining the base path (`/api/projects`) with the action path (like `/` or `{id}`).

Both exact paths and regex patterns are registered. The server also generates JSD documentation automatically by inspecting the method's parameters and annotations.

### 4.2 Available annotations

**`@Action(path = "/...", consume = "*/*")`**
Marks a method as a request handler. `path` is the URL sub-path relative to the endpoint's base path. `consume` filters by the request's Content-Type header (default `*/*` means accept anything).

**`@GET`, `@POST`, `@PUT`, `@DELETE`**
Specify which HTTP method the method handles. If none is specified, GET is assumed.

**`@Inject`**
Marks a field in an endpoint class for dependency injection. Currently, the only injectable type is `RestHttpRequest`. Before invoking the endpoint method, the server sets the `@Inject` field to the current request object so the method can access request data.

**`@Static`**
Marks a method whose response should be cached forever (static cache). The response is computed once and reused for all subsequent identical requests.

**`@Cache(timeToLive = ..., size = ..., status = ...)`**
Marks a method whose response should be cached for a specific time. `timeToLive` is in seconds, `size` is the maximum number of cached entries, and `status` is the HTTP status code this cache rule applies to. This annotation is repeatable, meaning you can put multiple `@Cache` annotations on the same method.

**`@CacheByStatus`**
A container annotation that holds multiple `@Cache` annotations. This is used internally — you do not need to write it yourself.

### 4.3 `RestActionService` - The method invocation engine

The file `modules/http/server/services/RestActionService.java` is the bridge between the HTTP layer and your Java code. Its `process(RestHttpRequest)` method does the following in order:

**Step 1: Inject dependencies**
It scans the endpoint object's fields for `@Inject` annotations. If a field is of type `RestHttpRequest`, the current request is assigned to that field. This is how endpoint methods get access to the request data.

**Step 2: Resolve the method**
Using `ReflectionUtil.getBestDescribedMethod()`, it finds the most specific declaration of the method. This handles cases where a method is declared in an interface and implemented in a class.

**Step 3: Check cache strategy**
It looks for `@Static`, `@Cache`, or `@CacheByStatus` annotations and determines the cache type (`STATIC`, `DYNAMIC`, or `NONE`).

**Step 4: Content-type negotiation**
If the `@Action` annotation specifies a `consume` value, the server checks whether the request's `Content-Type` header matches. If not, a 404 is returned.

**Step 5: Select model generator**
Based on the content type, the server picks the appropriate `ModelGenerator`:
- `application/json` → `JSONGenerator` (uses Gson to serialize Java objects to JSON)
- Other types → `TextGenerator` (calls `toString()` on the object)

**Step 6: Resolve method arguments**
This is the most complex step. The server needs to turn URL parts and request data into Java objects that can be passed to the method:

- For POST/PUT requests with a single parameter and no URL path variables: the raw POST body is passed directly.
- For methods with path variables (like `{id}` in the URL): `ReflectionUtil.valueFromType()` converts the string from the URL into the correct Java type (int, long, String, boolean, etc.). It supports: `String`, `Date` (formatted as `yyyy-MM-dd`), `int`/`Integer`, `boolean`/`Boolean`, `byte`/`Byte`, `char` (takes first character), `long`/`Long`, `short`/`Short`, `float`/`Float`, `double`/`Double`.
- If the parameter type is not one of the supported primitives, it delegates to the ModelGenerator for POJO (Plain Old Java Object) deserialization.

**Step 7: Check cache**
If caching is enabled, a cache key is generated by Base64-encoding the method signature and arguments. If a cached response exists for this key, it is returned immediately without invoking the method.

**Step 8: Invoke the method**
The method is called with the resolved arguments: `method.invoke(endpointObject, arguments)`. The return value is expected to be a `Response` object.

**Step 9: Populate cache**
If the method has caching annotations, the serialized response bytes are stored in the cache for future requests.

**Step 10: Error handling**
If any exception is thrown during this process, a 500 Internal Server Error response is returned with the exception message.

### 4.4 `ReflectionUtil.java` - Converting strings to Java types

The `valueFromType(Parameter, String)` method converts a string from a URL or request into the appropriate Java type based on the method parameter's declared type. For example:

- If the parameter type is `int`, the string `"42"` becomes the integer `42`
- If it is `boolean`, `"true"` becomes `true`
- If it is `char`, only the first character of the string is used
- If it is `Date`, the string is parsed as `yyyy-MM-dd` format

If the type is not recognized, it returns `null`, which signals the caller to try POJO deserialization instead.

---

## 5. The database module

The database module provides tools for connecting to databases and mapping database rows to Java objects.

### 5.1 `PooledDataSource.java` - Connection pooling

Opening a new database connection for every request is slow. `PooledDataSource` solves this by creating a pool of reusable connections.

**How it works:**

1. When created, the pool pre-populates itself with a given number of connections (the pool size). Each connection is opened using `DriverManager.getConnection(url, user, password)`.

2. Two lists track connections: `connectionPool` (idle connections ready to use) and `usedConnections` (connections currently in use).

3. When code calls `getConnection()`:
   - The pool validates itself (checks for dead connections in both lists)
   - A connection is removed from `connectionPool` and added to `usedConnections`
   - The connection is returned to the caller

4. When code calls `releaseConnection(connection)`:
   - The connection is moved from `usedConnections` back to `connectionPool`
   - It is now available for reuse

5. `validatePool()` runs before every `getConnection()` call. It checks each connection (both idle and used) with `isValid(timeout)`. Dead connections are closed and replaced with new ones.

6. All operations are `synchronized` to prevent race conditions when multiple threads use the pool.

### 5.2 `DataSourceManager.java` - Registry of connection pools

This is a static registry that stores named `DataSource` instances. It allows different parts of the program to share the same database connection pool:

```java
DataSourceManager.addDataSource("jdbc/pipeline", pooledDataSource);
// Later, anywhere in the code:
DataSource ds = DataSourceManager.getInstance("jdbc/pipeline");
```

If a name is not found in the local map, it falls back to JNDI lookup at `java:comp/env/<name>`.

### 5.3 `@Entity` and `@InjectConnection` annotations

Two annotations enable the mini-ORM system:

**`@Entity(manager = XxxManager.class)`**
Placed on entity classes (classes that represent database table rows). The `manager` attribute points to the class that handles database operations for this entity.

**`@InjectConnection`**
Placed on `Connection` fields inside manager classes. When a manager is created through the EntityManager, the current database connection is automatically assigned to fields marked with this annotation.

### 5.4 `EntityManager.java` - Creating managers and injecting connections

The `EntityManager` is a factory (a class that creates other objects) for entity managers. There are two `EntityManager` classes in the project — one in the database module and one in the infrastructure package. The one in the database module is the original:

**`create(Class entityClass)`**
1. Reads the `@Entity` annotation on the entity class
2. Instantiates the manager class specified in the annotation
3. If a connection is provided, scans the manager's fields for `@InjectConnection` and sets them using reflection
4. Returns the manager instance

**`instance(Class, Connection)`**
A simpler version that creates a plain instance without requiring the `@Entity` annotation, but still injects the connection.

This system means that manager classes never need to worry about how they get their database connection — it is provided automatically.

### 5.5 `TransactionableConnection.java` - Transaction management

A database transaction is a group of operations that either all succeed or all fail. `TransactionableConnection` wraps a standard JDBC `Connection` to add transaction support.

**States:**
- `AUTOCOMMIT` — each SQL statement is committed immediately
- `TRANSACTION` — statements are grouped until explicitly committed or rolled back

**Methods:**
- `beginTransaction()` — sends `BEGIN TRANSACTION` to the database and switches to TRANSACTION mode
- `commit()` — sends `COMMIT`, permanently saving all changes since `beginTransaction()`, then returns to AUTOCOMMIT mode
- `rollback()` — sends `ROLLBACK`, undoing all changes since `beginTransaction()`, closes the connection, returns to AUTOCOMMIT mode
- `close()` — if in TRANSACTION mode and an error occurred, rolls back (but does not close the connection so the transaction context remains). If in AUTOCOMMIT mode, rolls back on error then closes the connection

**Savepoint support:**
- `generateSavepoint()` — creates a savepoint with a unique name, allowing partial rollbacks within a transaction
- `rollbackToLastSavepoint()` — rolls back to the most recent savepoint
- `generateCustomSavepoint(name)` — creates a named savepoint
- `rollbackToCustomSavepoint(name)` — rolls back to a specific named savepoint

These use the SQL commands `SAVEPOINT SAVE_POINT_<name>` and `ROLLBACK TO SAVE_POINT_<name>`.

**Safety net:**
The `finalize()` method (called by the Java garbage collector before the object is destroyed) commits and closes the connection if it is still open. This prevents connection leaks.

### 5.6 `EntityManager.java` (infrastructure version)

The infrastructure package contains a second `EntityManager` class (`infrastructure/persistence/EntityManager.java`). This is a more advanced version used by the Pipeline Engine project. It adds:

- **ThreadLocal transaction context**: Each thread can have its own independent transaction. Nested transaction calls join the existing one through a reference-counting depth mechanism.
- **`transaction(work)` method**: Wraps a block of code in a transaction. If the code succeeds, the transaction commits. If it throws an exception, the transaction rolls back.

---

## 6. Resource handling and serving static files

### 6.1 Document root setup

The server needs to know where static files are located. This is set with `setDocumentRoot("web")`, which stores the path in the Application context. When resolving files, the path is normalized:

- Trailing slashes are stripped
- The absolute path is resolved
- A jail check ensures that resolved file paths stay within the document root

### 6.2 HTML template processing

When an HTML file is requested, it is not simply sent as-is. Instead:

1. The file is read from disk
2. POST parameters, query string parameters, and template variables (from `response.variable()`) are merged into a single property map. If a key exists in both query string and POST data, the query string value wins.
3. The HTML content and properties are passed to the cintix-html-engine library
4. The engine processes the HTML, replacing custom tags with generated content
5. The final HTML is returned to the browser

This means the same HTML file can show different content to different users based on the request parameters.

### 6.3 Non-HTML file serving

For other files (CSS, JavaScript, images, fonts):
- The MIME type is detected from the file extension
- The file is read and sent with the appropriate Content-Type header
- A Content-Length header is included so the browser knows how much data to expect

---

## 7. The caching system

### 7.1 `Cache.java` - In-memory storage

The file `infrastructure/Cache.java` is a generic in-memory key-value store built on `LinkedHashMap`. It can store any type of object, not just HTTP responses.

**Constructors:**
- `Cache(int maxItems)` — limits the number of entries
- `Cache(long timeToLive)` — entries expire after the given time in milliseconds
- `Cache(long timeToLive, int maxItems)` — both limits combined

**Cache types (from `CacheType.java`):**
- `STATIC` — entries never expire, regardless of TTL. Used for data that never changes.
- `DYNAMIC` — entries respect the TTL and are removed when expired.
- `NONE` — no caching.

**Operations:**
- `put(key, object, cacheType)` — stores an entry
- `get(key)` — retrieves an entry (returns null if expired or not found)
- `contains(key)` — checks if an entry exists and is still valid
- `renew(key)` — resets the creation timestamp, extending the TTL
- `remove(key)` — removes a specific entry
- `size()` — returns the number of entries
- `getAll()` — returns all valid entries as a list
- `clear()` — removes all entries
- `cleanup()` — removes all expired DYNAMIC entries. STATIC entries are never evicted by cleanup.

**Eviction behavior:**
When the cache is full (maxItems reached) and a new entry is added, the oldest entry (first key in insertion order) is removed. This is the behavior of `LinkedHashMap` with access-order `false`.

All operations are `synchronized` on the internal map, making the cache thread-safe.

### 7.2 How HTTP response caching works

When an endpoint method has `@Static` or `@Cache` annotations:

1. A cache key is generated by Base64-encoding the method's signature and the array of argument values. This means that calling the same method with different arguments produces different cache entries.

2. Before invoking the method, `RestActionService` checks if a valid cached response exists. If so, a `CachedResponse` is returned immediately.

3. After invoking the method, the serialized response bytes are stored in the cache for future use.

The static cache map `_CACHE_MAPS` is shared across all `RestActionService` instances in the JVM, keyed by the Base64 encoding of the method's `toString()`. This means different endpoints that call the same method share the same cache.

---

## 8. Logging

### 8.1 `Log.java` - File-based logger

The file `infrastructure/Log.java` is a singleton file-based logger. It writes to `log/server.log`.

**Log levels:**
- `log(message)` — informational message
- `error(message, exception)` — error with stack trace
- `warn(message)` — warning
- `debug(message)` — debug information

**Log format:**
```
[LEVEL][YYYY-MM-dd HH:mm:ss.SSS] message
```

**Log rotation:**
When `turn()` is called, the current log file is renamed using a numbering scheme: `server.log` → `server.log.1`, `server.log.1` → `server.log.2`, and so on. Old log files beyond `maxTurnOvers` (default 5) are deleted.

The `OutputStream` is opened lazily on the first write, not at startup.

---

## 9. HTTP client

### 9.1 `HTTPRestClient.java` - Making outbound requests

The file `infrastructure/HTTPRestClient.java` is an HTTP client for making requests to other servers. It is built on `java.net.HttpURLConnection` and is synchronous (blocking).

**Supported methods:**
- GET, POST, PUT, DELETE

**Content types:**
The `HTTPContentType` inner enum handles `application/json`, `application/xml`, `application/binary`, `application/form`, and a default type. This affects how the request body is encoded and how the response is interpreted.

**Key features:**
- **Timeouts**: Read timeout of 32 seconds, connect timeout of 16 seconds
- **Custom headers**: A `headersMap` allows setting arbitrary request headers
- **GZIP support**: Automatically handles gzip-compressed responses by wrapping input streams
- **Download**: The `download(path)` method returns an `InputStream` for binary file downloads
- **Response info**: After a request, you can access the response code, response headers, and redirect location

**Usage example:**
```java
HTTPRestClient client = new HTTPRestClient("https://api.example.com");
client.addHeader("Authorization", "Bearer token123");
String response = client.action("GET", "/data", null);
int statusCode = client.getResponseCode();
```

---

## 10. SSL/TLS security

### 10.1 `SSLContextManager.java` - Creating secure contexts

The security module can create SSL contexts for encrypted HTTPS connections. It uses Java's built-in `KeyManagerFactory` and `TrustManagerFactory` with the "SunX509" algorithm.

The key store is loaded from a `.keystore` file (JKS format) in the working directory.

### 10.2 `SignedBy.java` - Certificate identity

This data class holds the certificate's subject information:
- `commonName`, `organizationalUnit`, `organization`
- `city`, `state`, `country`
- `alias`
- `validity` — default is about 9 years (3288 days)

Default values identify the certificate as belonging to Cintix in Denmark.

### 10.3 `SSLCertificateManager.java` - Loading certificates

Loads a `.keystore` file (JKS format) from the working directory and configures the Java runtime to trust it by setting the `javax.net.ssl.trustStore` and `javax.net.ssl.keyStorePassword` system properties.

Note: The SSL context is separate from `RestHttpServer`. The server itself runs on plain HTTP. To create an HTTPS server, you would need to wrap the server socket channel with an SSL engine — this is not built into the current `RestHttpServer`.

---

## 11. JSON Service Description (JSD)

### 11.1 Automatic API documentation

When a request comes in with `?jsd` appended to the URL, the server returns machine-readable documentation about the endpoint. This is generated by `JsonServiceDescriptionEngine`.

The documentation includes:
- **Service name and URI**
- **Actions**: For each method, the HTTP method, URL, accepted content types, and caching strategy
- **Arguments**: For each method parameter, the name, type, and for complex types, the nested field structure
- **Cache rules**: From `@Cache` and `@Static` annotations

The response is a JSON document describing the entire API surface of the endpoint. This is useful for API discovery and for generating client code.

### 11.2 JSD model classes

The `jsd/models/` package contains the data structures:
- `API` — top-level container holding a list of `Service` objects
- `Service` — represents one endpoint with its name, URI, and list of actions
- `ServiceAction` — represents one method with its HTTP action, URI, accepted types, and arguments
- `ArgumentDefinition` — describes a single parameter with name and type
- `ModelDefinition` — for complex types, describes the nested fields
- `Cache` — describes a cache rule with status code, description, and TTL

---

## 12. Testing

### 12.1 Custom test framework

The project does not use JUnit. Instead, it has its own minimal testing framework:

- `TestSupport.java` provides assertion methods: `assertTrue`, `assertFalse`, `assertEquals`, `assertArrayEquals`, `assertNull`
- `AllTests.java` is a test runner that instantiates each test class and calls its `runAll()` method

### 12.2 Mock JDBC framework

The `MockJdbc.java` class in the test directory is a complete mock JDBC implementation using `java.lang.reflect.Proxy`. It:

- Creates mock `Connection`, `Statement`, and `ResultSet` objects
- Tracks all executed SQL in a list
- Uses a `MockDriver` that accepts `jdbc:mock:*` URLs so you can test database code without a real database
- Tracks connection state (open/closed, statement close count)

### 12.3 What the tests cover

| Area | Test file | What it checks |
|------|-----------|----------------|
| HTTP parsing | `HttpUtilTest` | Query string extraction, header parsing, URL building |
| Template properties | `ServerPagePropertyMergeTest` | Query parameters override POST parameters |
| Document root | `RestHttpServerPathTest` | Path normalization |
| Cache keys | `RestActionCacheKeyTest` | Different arguments produce different cache entries |
| Entity manager | `EntityManagerInjectionTest` | Connection injection into managers |
| Connection pool | `PooledDataSourceTest` | Pool lifecycle, dead connection replacement |
| Transactions | `TransactionableConnectionTest` | Commit and rollback behavior |
| MIME types | `MimeTypesTest` | Type resolution, default for unknowns |
| Byte streams | `ByteMemoryStreamTest` | Append behavior, null handling |
| Cache | `CacheTest` | Store/retrieve, TTL expiry, STATIC vs DYNAMIC |

---

## 13. Utility classes

### 13.1 `ByteMemoryStream.java`

A simple byte array accumulator:
- `writeBytes(byte[] content)` appends bytes to an internal array using `System.arraycopy`
- `toByteArray()` returns all accumulated bytes
- Null or empty input is ignored

### 13.2 `Status.java`

An enum of HTTP status codes with their integer values: `OK(200)`, `Created(201)`, `Accpeted(202)`, `NoContent(204)`, `MovedPermanently(301)`, `MovedTemporary(302)`, `BadRequest(400)`, `Unauthorized(401)`, `Forbidden(403)`, `NotFound(404)`, `InternalServerError(500)`, `BadGateway(502)`, `ServiceUnavailable(503)`, `All(-1)`.

---

## 14. Overview: The big picture

To summarize the entire system in a coherent story:

**What this project is:**
Cintix Application Server is a lightweight Java library for building web applications. It is not a standalone program — it is a set of tools that other programs (like the Cintix Pipeline Engine) include and extend.

**The core — HTTP server:**
The heart of the library is `RestHttpServer`, an abstract class built on Java NIO. It uses a single-threaded event loop with a selector to handle many simultaneous connections. When a web request arrives, it goes through four stages: accept the connection, read and parse the HTTP request, route the request to the correct handler, and write the response back to the client.

**How requests find their handler:**
The server first checks if the URL matches a static file in the document root. HTML files are processed through the cintix-html-engine templating system. Other files are served directly. If the URL does not match a file, the server searches its registered endpoints. Paths like `/project/{id}` are converted to regular expressions that capture variable parts of the URL. The matching endpoint method is called via reflection.

**How endpoint methods are invoked:**
`RestActionService.process()` handles the invocation. It injects the current request into fields marked with `@Inject`, resolves method arguments from URL path variables and request data, checks for cached responses, invokes the method, and caches the result if the method has caching annotations. The method returns a `Response` object which is serialized to raw HTTP bytes and sent over the network.

**The Response builder:**
The `Response` class uses a fluent API for building HTTP responses. You chain method calls: `new Response().OK().model(data)`. The `build()` method assembles the status line, headers, and body into a complete HTTP response.

**Database access:**
The database module provides connection pooling (`PooledDataSource`), a registry for sharing pools (`DataSourceManager`), transaction support (`TransactionableConnection`), and a mini-ORM (`EntityManager`) that automatically creates manager objects and injects database connections into fields marked with `@InjectConnection`.

**Caching:**
The `Cache` class provides thread-safe in-memory caching with optional TTL and size limits. HTTP responses can be cached at the endpoint level using `@Static` (permanent) or `@Cache` (time-based) annotations. The cache key is derived from the method signature and arguments.

**Other services:**
- File-based logging with rotation (`Log`)
- HTTP client for outbound requests (`HTTPRestClient`)
- SSL/TLS context creation for secure connections
- Automatic API documentation generation (JSON Service Description)

**Thread model:**
Everything runs on a single thread — the NIO selector thread. This keeps the design simple but means that slow operations (long database queries, heavy computation) will block the entire server. The library is designed for applications where request handling is quick.

**Dependencies:**
The only external JARs are Gson (JSON), PostgreSQL JDBC driver, and the cintix-html-engine (server-side rendering). There is no Spring, no Jakarta EE, no external logging framework — just the Java standard library and three small libraries.
