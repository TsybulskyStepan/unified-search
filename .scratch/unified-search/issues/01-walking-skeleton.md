# 01 — Walking skeleton: Spring Boot on pgvector via docker compose

**What to build:** A clean clone runs `docker compose up` and gets a healthy service on a migrated
database. The app boots Spring Boot on Java 25, Flyway applies the initial schema — the three tables
and the three Postgres extensions they depend on — and the health endpoint reports success including
database connectivity. The three modules exist as packages with the import boundary already enforced,
so no later ticket can quietly cross it. A Testcontainers base class gives every later integration
test a real pgvector database rather than a substitute that lacks the extensions.

The container image arrives here rather than late (system-design §15 puts it at step 8) because
§11.4's compose file runs the app container: the one command the brief grades cannot work until the
image exists. Building it first keeps that command green for every ticket after this one.

**Blocked by:** None — can start immediately.

**Status:** complete

- [x] `docker compose up` from a clean clone yields a healthy app and database, the app waiting on the database healthcheck
- [x] Flyway applies the §3.1 schema; the three extensions and three tables exist, with `embedding_model` part of the chunk primary key
- [x] `GET /health` returns 200 and reports unhealthy when the database is unreachable
- [x] The `onboarding`, `search` and `shared` packages exist, and an ArchUnit test fails the build if the two feature modules import each other
- [x] An integration test base class starts `pgvector/pgvector:pg17` via Testcontainers and is exercised by at least one passing test
- [x] The IntelliJ scaffolding class is gone and `./gradlew check` passes
