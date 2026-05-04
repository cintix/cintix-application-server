# Repository Guidelines

## Project Structure & Module Organization
Core Java sources live under `src/dk/cintix/application/server`, organized by concern:
- `rest/` and `rest/http/` for REST annotations, endpoints, request/response handling.
- `jdbc/` for datasource/entity/connection management.
- `ssl/`, `events/`, `io/`, and `model/` for infrastructure utilities.

Build output goes to `build/` and distributables to `dist/`. Third-party jars are vendored in `lib/`. Tests are expected in `test/` (mirroring package paths under `src/`). NetBeans project metadata is in `nbproject/`.

## Build, Test, and Development Commands
Use Apache Ant from repo root:
- `ant clean` removes `build/` artifacts.
- `ant compile` compiles Java sources.
- `ant test` runs unit tests from `test/`.
- `ant jar` creates `dist/cintix-application-server.jar`.
- `ant default` runs full build + tests (CI-friendly baseline).
- `ant run` runs the configured main class from the project config.

If you work in containers, review `Dockerfile` and `run-docker.sh` before localizing changes.

## Coding Style & Naming Conventions
- Java 8 target (`javac.source=1.8`, `javac.target=1.8`).
- Use 4-space indentation, UTF-8 encoding, and braces on all control blocks.
- Keep package names lowercase (`dk.cintix...`), classes in `PascalCase`, methods/fields in `camelCase`, constants in `UPPER_SNAKE_CASE`.
- Keep REST annotations and endpoint classes close to related HTTP logic in `rest/`.

## Testing Guidelines
- Place tests in `test/` with matching package structure (example: `test/dk/cintix/application/server/rest/...`).
- Name test classes `*Test.java`; test methods should describe behavior (example: `returns404WhenRouteMissing`).
- Run `ant test` before opening a PR. Add regression tests for fixes in `rest`, `jdbc`, or SSL handling.

## Commit & Pull Request Guidelines
Recent history favors short, imperative commit subjects (examples: `removed debug`, `changed dependencies`). Prefer clearer variants like `Remove debug logging from RestHttpServer`.

For pull requests:
- Explain what changed and why.
- Link related issues/tasks.
- List verification steps and commands run (for example, `ant default`).
- Include API behavior notes or sample request/response when endpoint behavior changes.
