# Unified Search

A search API over advisors' clients and their documents. One query goes to `/search`, and one ranked
list comes back holding both kinds of result, each carrying why it matched.

One Spring Boot service (Java 25) on one PostgreSQL 17 database with `pgvector`, `pg_trgm` and
`citext`. No broker, no vector service, no second datastore. Embeddings run inside the process, so a
clean clone needs no credentials and no network.

| | |
|---|---|
| How it is built, and why | [docs/system-design.md](docs/system-design.md) |
| What the product must do | [docs/prd.md](docs/prd.md) |
| The original brief | [docs/assignment.md](docs/assignment.md) |

## Setup

One command, no credentials, nothing to configure:

```bash
docker compose up -d
curl -s localhost:8080/health     # wait for {"status":"UP"}
```

First start takes a minute or two: it builds the image, runs migrations and seeds a demo corpus of
**50 clients and 126 documents**, embedding every chunk locally.

```bash
docker compose down -v && docker compose up -d   # reset to a clean database
```

You need that reset if **`NevisWealth` returns `[]`**. The seeder deliberately skips a database that
already has clients in it, so a single client created before the first successful seed leaves the
corpus absent and every example below empty. `down -v` drops the volume, which is what makes the
next start seed again.

The API key is a dev-only default baked into `docker-compose.yaml`. Export it once and every command
below is copy-pasteable:

```bash
export KEY=dev-only-insecure-key-do-not-use-in-production-env
```

Interactive docs, no key required: <http://localhost:8080/swagger-ui.html>. The SPA is on
<http://localhost:8080/>. Every endpoint also exists as a runnable request in [`http/`](http/) — six
`.http` files for VS Code or IntelliJ, covering clients, documents, search, summaries and error
shapes.

## How it works

```
query ─► plan ─┬─► client search (trigram, identity + context tiers) ──┐
               │                                                       ├─► order ─► page ─► hydrate
               └─► document retrieval ─┬─ label   (tags)               │
                                       ├─ lexical (tsvector)           │
                                       └─ semantic (pgvector) ─► fuse ─┘
```

**Plan.** The query is normalised, then split into the client it names (if any), the residual text,
and the taxonomy labels that text refers to. `John's bill` names John and asks for bills; `bill` alone
does not name Bill Carter.

**Retrieve.** Client search and the three document signals run concurrently on virtual threads.
Documents are fused with reciprocal rank fusion over the two *ranked* signals; a label match is a
boolean tier flag rather than a score.

**Order.** Deterministic tiers chosen by the plan's shape, never a blended score. Because the order is
total, a page is a slice and deep pages are stable.

[The system design](docs/system-design.md) carries the schema, the API contract, the retrieval detail
and the reasoning behind each choice.

## Tests

```bash
./gradlew check          # format, compile, unit and integration tests
```

Integration tests need Docker; they run the real schema on `pgvector/pgvector:pg17` through
Testcontainers and the real embedding model, not stubs.

Relevance is guarded by an evaluation set rather than by taste. It holds **34 queries**: 26 declare a
positive expectation (an exact first result, full recall within *n* positions, or a compound ordering)
and 8 are negatives that must return nothing. Every document query also asserts that no client
outranks an expected document. The last run logged:

```
eval summary queries=34 MRR=1.0 meanRecall@n=0.9615384615384616
```

It also re-derives the semantic floor and fails the build if the margin it depends on disappears.