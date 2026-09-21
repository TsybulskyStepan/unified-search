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

## Example queries and responses

Every response below was copied from a run of this code against a freshly seeded database. Where a
response is long it is trimmed, never edited: `…` marks elided prose, and the JSON blocks drop `id`,
`client_id`, `created_at`, `summary` and `social_links` to keep the shape readable.

### 1. A company name inside an email address finds the client

```bash
curl -s "localhost:8080/search?q=NevisWealth&limit=5" -H "X-API-Key: $KEY"
```

```json
[
  {
    "type": "client",
    "score": 1.0,
    "match": { "field": "email", "tier": "identity" },
    "client": {
      "first_name": "John",
      "last_name": "Doe",
      "email": "john.doe@neviswealth.com",
      "description": "Long-standing private client, onboarded through the NevisWealth referral programme. …"
    }
  }
]
```

`pg_trgm` splits the address into `john`, `doe`, `neviswealth`, `com`, so the company name matches the
email at 1.0. `match.tier` says this was an identity field, which is what lets it outrank documents.

### 2. A KYC category finds documents that never use the words

```bash
curl -s "localhost:8080/search?q=address%20proof&limit=15" -H "X-API-Key: $KEY"
```

The brief asks that `address proof` also return documents containing "utility bill". It does.
`X-Total-Count` is `73`. The first page is bank statements, tenancy agreements and a council tax bill;
entry 13 is the document the brief names:

```json
[
  { "type": "document", "score": 0.029877,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["purpose:proof_of_address"] },
    "document": { "title": "Savings Account Statement Q3 2024", "document_type": "bank_statement" } },

  … entries 2 to 12: six more statements, four tenancy agreements, a Council Tax Bill …

  { "type": "document", "score": 0.025522,
    "match": {
      "passage": "account holder's name and confirms occupancy at the registered address for the period shown above.",
      "signals": ["label", "lexical", "semantic"],
      "labels": ["purpose:proof_of_address"]
    },
    "document": {
      "title": "2024 Utility Bill", "client_name": "John Doe",
      "document_type": "utility_bill", "purposes": ["proof_of_address"],
      "classification_source": "rule", "summary_status": "none"
    } }
]
```

**None of the documents on that page contains the phrase "address proof" or "proof of address"
anywhere in its title or content.** They match because they are *tagged* `proof_of_address`, and the
label text is indexed alongside the content, so the tag is reachable both lexically and semantically.
That is the difference between this and cosine over raw text.

`match.signals` names which of the three signals admitted each document, and `match.labels` names the
tag that matched, so a surprising ranking can be explained from the response alone.

### Other query shapes

**A fuzzy name.** A misspelling still finds the client, through trigram similarity rather than an
index of corrections — `?q=Hendersen` returns Mary Henderson at 0.7.

**An identity query** returns people, not their paperwork. `?q=John` returns both Johns — John Doe and
John Whitfield — each at 1.0 on `match.field: "name"`, and no documents.

**A category query** returns documents and no clients at all, even though a client named *Bill Carter*
exists:

```bash
curl -s "localhost:8080/search?q=utility%20bill&limit=5" -H "X-API-Key: $KEY"
```

```json
[
  { "type": "document", "score": 0.032787,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "2024 Utility Bill", "client_name": "John Doe", "document_type": "utility_bill" } },
  { "type": "document", "score": 0.032258,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "2024 Utility Bill", "client_name": "Samuel Okafor", "document_type": "utility_bill" } }
]
```

**A compound query** names a person *and* a category, and the person becomes a qualifier rather than
the answer:

```bash
curl -s "localhost:8080/search?q=John%20Doe%20utility%20bill&limit=5" -H "X-API-Key: $KEY"
```

```json
[
  { "type": "document", "score": 0.032787,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "2024 Utility Bill", "client_name": "John Doe", "document_type": "utility_bill" } },
  { "type": "document", "score": 0.009174,
    "match": { "signals": ["semantic"], "labels": [] },
    "document": { "title": "Advisory Engagement Letter", "client_name": "John Doe", "document_type": "engagement_letter" } },
  { "type": "client", "score": 1.0,
    "match": { "field": "email", "tier": "identity" },
    "client": { "first_name": "John", "last_name": "Doe", "email": "john.doe@neviswealth.com" } },
  { "type": "document", "score": 0.032258,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "2024 Utility Bill", "client_name": "Samuel Okafor", "document_type": "utility_bill" } }
]
```

Read the order: John's bill, then his other qualifying document, then John himself, then everyone
else's bills. Note the second entry was admitted by the semantic signal alone, with no label and no
shared words.

**A query with no honest answer** returns `[]` rather than the nearest thing in the corpus.
`?q=how%20to%20bake%20sourdough%20bread` and `?q=sdfewferdvrevrennfg` both return `[]` — the second
because a readability check rejects text the embedding model cannot read as words, which no cosine
threshold can do.

## Summaries (optional)

Summaries are the one feature that sends document text to a third party, so they are **off unless you
opt in** — and fully observable without a key, because the failure is a state, not an error. Seeded
ids are random per volume, so find one first:

```bash
CID=$(curl -s "localhost:8080/search?q=NevisWealth" -H "X-API-Key: $KEY" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["client"]["id"])')
DID=$(curl -s "localhost:8080/clients/$CID/documents" -H "X-API-Key: $KEY" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')

curl -s      "localhost:8080/clients/$CID/documents/$DID"         -H "X-API-Key: $KEY"  # summary_status: none
curl -s -X POST "localhost:8080/clients/$CID/documents/$DID/summary" -H "X-API-Key: $KEY"  # 202, pending
curl -s      "localhost:8080/clients/$CID/documents/$DID"         -H "X-API-Key: $KEY"  # a second later: failed
```

`none → pending → failed`, with no key, in about a second. The same path handles a bad or revoked key,
so the degradation you see locally is the one that runs in production. To turn summaries on, copy
`.env.example` to `.env` and set `GEMINI_API_KEY`, then restart; the state machine is identical and
ends at `ready`.

**Egress.** With a key set, a document's title and content are sent to the Google Gemini API when a
summary is requested — never on create, never on read, never during search. Leaving `GEMINI_API_KEY`
unset removes that egress entirely; nothing else in the system makes an outbound call.

**Search never depends on it.** The document above is `failed` and still ranks first for
`utility bill`. Search never reads the `summary` column.

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

## The taxonomy, and what it costs to change

The closed vocabulary of document types and KYC purposes lives in
`src/main/resources/taxonomy/taxonomy.yaml`. It is the one part of this system a reader is expected to
edit. Adding a type means touching four things:

1. The type entry in `taxonomy.yaml`.
2. The `version` at the top of that file. Raising it is what triggers reclassification.
3. `src/test/resources/eval/classification.json`, which asserts the expected type of every seed
   document and must stay at 100%.
4. The evaluation queries, if the new type should answer a question that is measured.

**Reclassification runs at startup**, in batches of 100, without blocking readiness. A row not yet
reached is searchable under its *old* labels, so the window is briefly stale, never absent.

## Deviations from the brief

| Deviation | Why |
|---|---|
| **Endpoints added** beyond the three specified: `GET /clients`, `GET /clients/{id}`, `GET /clients/{id}/documents`, `GET /clients/{id}/documents/{documentId}`, `POST …/summary`, `GET /health`, OpenAPI, Swagger UI, and a React SPA at `/` | The reads let a reviewer confirm what was written without opening a database, and search is easier to judge through a search box |
| **Search results are a union**, not the brief's `Document`: tagged `client` or `document`, carrying a `match` object | The brief invites extending the model, and without provenance a ranking cannot be argued with |
| **Document results omit `content`** | A result returns the matching passage instead; the full text is one `GET` away. Returning 126 full documents per page is a bandwidth decision disguised as a schema one |
| **The embedding input is not the raw content**: each body chunk is embedded as `title + "\n\n" + chunk`, plus one synthetic label chunk per document | Every chunk carries its document's title, and the tag is reachable semantically. Stored vectors therefore do not map one-to-one onto spans of the original text, though returned passages always do |
| **Pagination and an API key**: `limit`/`offset` with `X-Total-Count`, `X-API-Key` on every data endpoint | Health, OpenAPI, Swagger UI and the SPA's static assets are exempt, because a browser cannot attach a header when fetching them |

## Known limits

**The semantic floor is a coarse gate, not a tuned threshold.** At `0.238` it does not cleanly
separate near-domain noise from genuine paraphrase: `cheap hotel deals in Rome` scores 0.328 against a
lease, while a real paraphrase such as `evidence of where the client lives` scores as low as 0.12
against one electricity bill. Those ranges overlap, so raising the floor would drop real answers
before it dropped travel noise. Such matches rank below every label and lexical hit. The remedies are
a reranker or intent prototypes, not a better number.

**A lexical false positive.** `weather forecast for the weekend` returns the client *Zoë Fairweather*
at 0.75, because trigram similarity finds `weather` inside her email address — a real consequence of
the 0.6 floor that makes `Hendersen → Henderson` work at 0.70.

**Short-name typos.** `jhon` (0.20) and `joe` (0.50) against "John Doe" cannot clear any floor that
keeps the rest of the corpus correct. Queries of one or two characters, non-English synonyms and
non-English stemming are all out of scope.

Load testing, multi-tenancy, an ANN index, update/delete endpoints and real authentication were
[cut on purpose](docs/prd.md#83-cuts-taken-deliberately); the remaining ideas and their order are in
[the design's follow-ups](docs/system-design.md#follow-ups-and-ideas-not-adopted).

**No latency is measured.** The figures in
[the design](docs/system-design.md#performance-and-capacity) are budget arithmetic, not benchmarks;
Micrometer timers are in place so the real numbers can be taken in operation.

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

## Deploy (GCP Cloud Run)

One Cloud Run service against a Cloud SQL Postgres instance, secrets from Secret Manager.
[The design](docs/system-design.md#cloud-run-production) covers the shape and the reasoning; these are
the commands, and they match the running service. There is no pipeline file: the image is built and
pushed by hand, then deployed.

**Once.** The database needs the `unified_search` schema and the `vector`, `pg_trgm` and `citext`
extensions. Flyway creates every table and index on first startup. Create one Secret Manager secret
each for the database password and the API key (at least 32 characters), and a third for
`GEMINI_API_KEY` only if you want summaries.

**Build.** Cloud Run runs `linux/amd64`, so say so when building on an ARM machine:

```bash
IMAGE=europe-west4-docker.pkg.dev/PROJECT/unified-search/unified-search:$(git rev-parse --short HEAD)-amd64
docker buildx build --platform linux/amd64 --push -t "$IMAGE" .
```

**Deploy.** The startup probe is long because the embedding model loads before the port opens:

```bash
gcloud run deploy unified-search --region=europe-west4 --image="$IMAGE" \
  --service-account=RUN_SERVICE_ACCOUNT \
  --cpu=1 --memory=2Gi --max-instances=1 --cpu-boost --allow-unauthenticated \
  --add-cloudsql-instances=PROJECT:europe-west4:INSTANCE \
  --startup-probe=tcpSocket.port=8080,periodSeconds=240,timeoutSeconds=240,failureThreshold=1 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=cloudrun,SEED_ENABLED=true,DB_USER=DB_USER,\
SPRING_FLYWAY_DEFAULT_SCHEMA=unified_search,SPRING_FLYWAY_SCHEMAS=unified_search,\
DB_URL=jdbc:postgresql:///DB_NAME?cloudSqlInstance=PROJECT:europe-west4:INSTANCE&socketFactory=com.google.cloud.sql.postgres.SocketFactory&sslmode=disable&currentSchema=unified_search,public" \
  --set-secrets="DB_PASSWORD=DB_PASSWORD_SECRET:latest,API_KEY=API_KEY_SECRET:latest"
# summaries: append ,GEMINI_API_KEY=GEMINI_SECRET:latest to --set-secrets
```

`--allow-unauthenticated` only lets the request reach the app; every data endpoint still needs
`X-API-Key`. Cloud Run assigns a `*.run.app` HTTPS URL, used exactly like the local endpoint:

```bash
gcloud run services describe unified-search --region=europe-west4 --format='value(status.url)'
```
