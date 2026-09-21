# Unified Search

A search API over advisors' clients and their documents. One query goes to `/search`, and one ranked
list comes back holding both kinds of result, each carrying why it matched.

One Spring Boot service (Java 25) on one PostgreSQL 17 database with `pgvector`, `pg_trgm` and
`citext`. No broker, no vector service, no second datastore. Embeddings run inside the process, so a
clean clone needs no credentials and no network.

Every response below was copied from a run of this code against a freshly seeded database. Where a
response is long it is trimmed, never edited: `…` marks elided prose, and the JSON blocks drop
`id`, `client_id`, `created_at`, `summary` and `social_links` to keep the shape readable. Send the
request yourself and you get the same values with those fields present.

## Why it is not a thin wrapper around an embedding model

The brief budgets 10 to 14 hours, and this is more than that, so the extra structure should justify
itself before you read the architecture.

The obvious build — embed the documents, embed the query, sort by cosine — was built first. It failed
four of eight evaluation queries. Swapping MiniLM for E5 reproduced the same four failures, which
ruled out the model and pointed at the shape of the problem: similarity has no notion of what a
document is *for*.

| Query | Similarity alone | Why | Fix |
|---|---|---|---|
| `tax residency` | Council Tax Bills first | The bills literally say "residency", and word overlap beats meaning for any embedding model | Documents carry KYC purpose labels. The bills are `proof_of_address`; the W-9 and tax return are `tax_status` |
| `source of funds` | Engagement letter first | The completion statement's answer sits in one sentence inside a chunk full of currency figures | A `source_of_funds` label, and one label chunk per document that is immune to dilution |
| `proof of address` | 5 of 7 found | Two bills never use address wording at all | Label retrieval admits every tagged document, whatever its wording |
| `advisory fees` | Client "Grace Kim" first | Her description says "advisory arrangements", and clients outranked every document | Description hits became a context tier ranked *below* documents |

So documents are classified at write time into a small closed vocabulary, queries are parsed into a
plan before retrieval, and three signals are fused rather than one. That is the whole of the extra
complexity, and each piece traces back to a row in that table.

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

Everything below was produced this way: a `down -v` onto an empty volume, then one `docker compose
up -d` with no `.env` and no credentials of any kind.

You need that reset if **`NevisWealth` returns `[]`**. The seeder deliberately skips a database that
already has clients in it, so a single client created before the first successful seed leaves the
corpus absent and every example below empty. `down -v` drops the volume, which is what makes the
next start seed again.

The API key is a dev-only default baked into `docker-compose.yaml`. Every request below sends it:

```
X-API-Key: dev-only-insecure-key-do-not-use-in-production-env
```

Interactive docs, no key required: <http://localhost:8080/swagger-ui.html>. The SPA is on
<http://localhost:8080/>. Every endpoint also exists as a runnable request in [`http/`](http/) — six
`.http` files for VS Code or IntelliJ, covering clients, documents, search, summaries and error
shapes, with the same reset note.

## The two cases from the brief

### 1. A company name inside an email address finds the client

```bash
curl -s "localhost:8080/search?q=NevisWealth&limit=5" \
  -H "X-API-Key: dev-only-insecure-key-do-not-use-in-production-env"
```

```json
[
  {
    "type": "client",
    "score": 1.0,
    "match": { "field": "email", "tier": "identity" },
    "client": {
      "id": "c6fbf6c7-28d6-435e-986b-07acd4c06826",
      "first_name": "John",
      "last_name": "Doe",
      "email": "john.doe@neviswealth.com",
      "description": "Long-standing private client, onboarded through the NevisWealth referral programme. …",
      "social_links": ["https://www.linkedin.com/in/john-doe-nevis", "https://www.linkedin.com/company/neviswealth"]
    }
  }
]
```

`pg_trgm` splits the address into `john`, `doe`, `neviswealth`, `com`, so the company name matches the
email at 1.0. `match.tier` says this was an identity field, which is what lets it outrank documents.

### 2. A KYC category finds documents that never use the words

```bash
curl -s "localhost:8080/search?q=address%20proof&limit=15" \
  -H "X-API-Key: dev-only-insecure-key-do-not-use-in-production-env"
```

The brief asks that `address proof` also return documents containing "utility bill". It does. The
response is an array; the first page is bank statements, tenancy agreements and a council tax bill,
and the thirteenth entry is the one the brief names:

```json
[
  { "type": "document", "score": 0.029877,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["purpose:proof_of_address"] },
    "document": { "title": "Savings Account Statement Q3 2024", "document_type": "bank_statement" } },

  { "type": "document", "score": 0.029551,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["purpose:proof_of_address"] },
    "document": { "title": "Bank Statement October 2025", "document_type": "bank_statement" } },
```

Entries 3 to 12 are five more statements, four tenancy agreements and a Council Tax Bill, all scoring
between the second entry and the last. Entry 13 is the document the brief asks for:

```json

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

`X-Total-Count` is `73`; 55 of those carry the `proof_of_address` label and the rest are lexical or
semantic matches ranked below them.

**None of the documents on that page contains the phrase "address proof" or "proof of address"
anywhere in its title or content.** They match because they are *tagged* `proof_of_address`, and the
label text is indexed alongside the content, so the tag is reachable both lexically and semantically.
That is the difference between this and cosine over raw text.

`match.signals` names which of the three admitted each document, and `match.labels` names the tag that
matched, so a surprising ranking can be explained from the response alone.

## Other query shapes

Each of these sends the same header as above. It is written `-H "X-API-Key: $KEY"` for width; export
it once and every command here is copy-pasteable:

```bash
export KEY=dev-only-insecure-key-do-not-use-in-production-env
```

**A fuzzy name.** A misspelling still finds the client, through trigram similarity rather than an
index of corrections:

```bash
curl -s "localhost:8080/search?q=Hendersen&limit=3" -H "X-API-Key: $KEY"
```

```json
[ { "type": "client", "score": 0.7,
    "match": { "field": "name", "tier": "identity" },
    "client": { "first_name": "Mary", "last_name": "Henderson", "email": "mary.henderson@example.com" } } ]
```

**An identity query** returns people, not their paperwork:

```bash
curl -s "localhost:8080/search?q=John&limit=5" -H "X-API-Key: $KEY"
```

```json
[
  { "type": "client", "score": 1.0,
    "match": { "field": "name", "tier": "identity" },
    "client": { "first_name": "John", "last_name": "Doe", "email": "john.doe@neviswealth.com" } },
  { "type": "client", "score": 1.0,
    "match": { "field": "name", "tier": "identity" },
    "client": { "first_name": "John", "last_name": "Whitfield", "email": "j.whitfield@whitfield-consulting.example" } }
]
```

Both Johns, and no documents: an identity query is answered by people.

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
    "document": { "title": "2024 Utility Bill", "client_name": "Samuel Okafor", "document_type": "utility_bill" } },
  { "type": "document", "score": 0.030798,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "Water Services Bill 2024/25", "client_name": "Zoë Fairweather", "document_type": "utility_bill" } }
]
```

**A compound query** names a person *and* a category, and the person is treated as a qualifier rather
than the answer:

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
    "document": { "title": "2024 Utility Bill", "client_name": "Samuel Okafor", "document_type": "utility_bill" } },
  { "type": "document", "score": 0.030798,
    "match": { "signals": ["label", "lexical", "semantic"], "labels": ["type:utility_bill"] },
    "document": { "title": "Water Services Bill 2024/25", "client_name": "Zoë Fairweather", "document_type": "utility_bill" } }
]
```

Read the order: John's bill, then his other qualifying document, then John himself, then everyone
else's bills. The person he named is a filter on the answer, not the answer — and note the second
entry was admitted by the semantic signal alone, with no label and no shared words.

**A query with no honest answer** returns `[]` rather than the nearest thing in the corpus.
`?q=how%20to%20bake%20sourdough%20bread` and `?q=sdfewferdvrevrennfg` both return `[]` — the second
because a readability check rejects text the embedding model cannot read as words, which no cosine
threshold can do.

## Summaries (optional)

Summaries are the one feature that sends document text to a third party, so they are **off unless you
opt in**. They are also fully observable without a key, because the failure is a state, not an error.

With no key — the default — request one and watch the state machine. Seeded ids are random per
volume, so find one first:

```bash
export KEY=dev-only-insecure-key-do-not-use-in-production-env
CID=$(curl -s "localhost:8080/search?q=NevisWealth" -H "X-API-Key: $KEY" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["client"]["id"])')
DID=$(curl -s "localhost:8080/clients/$CID/documents" -H "X-API-Key: $KEY" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')
```

```bash
# 1. before
curl -s "localhost:8080/clients/$CID/documents/$DID" -H "X-API-Key: $KEY"
```
```json
{ "title": "2024 Utility Bill", "summary": null, "summary_status": "none" }
```

```bash
# 2. request it → HTTP 202
curl -s -X POST "localhost:8080/clients/$CID/documents/$DID/summary" -H "X-API-Key: $KEY"
```
```json
{ "title": "2024 Utility Bill", "summary": null, "summary_status": "pending" }
```

```bash
# 3. read it again about a second later
curl -s "localhost:8080/clients/$CID/documents/$DID" -H "X-API-Key: $KEY"
```
```json
{ "title": "2024 Utility Bill", "summary": null, "summary_status": "failed" }
```

`none → pending → failed`, with no key, in about a second. The same path handles a bad or revoked key,
so the degradation you see locally is the one that runs in production.

To turn summaries on, copy `.env.example` to `.env` and set `GEMINI_API_KEY`, then restart. The state
machine is identical and ends at `ready` with the summary text stored. `SUMMARY_MODEL` overrides the
model id without a rebuild, which matters because Google retires ids on its own schedule.

**Egress.** With a key set, a document's title and content are sent to the Google Gemini API when a
summary is requested — never on create, never on read, never during search. Leaving `GEMINI_API_KEY`
unset removes that egress entirely; nothing else in the system makes an outbound call. Embeddings are
computed in-process precisely so that the mandatory path has no third-party data flow.

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
does not name Bill Carter, because a single token that is also a category word is treated as a
category unless it was possessive or a second token confirmed the person.

**Retrieve.** Client search and the three document signals run concurrently on virtual threads.
Documents are fused with reciprocal rank fusion over the two *ranked* signals; a label match is a
boolean tier flag rather than a score, because "is tagged proof of address" is not a quantity.

**Order.** Deterministic tiers chosen by the plan's shape, never a blended score. Identity clients
outrank documents; description-only matches rank below them. Because the order is total, a page is a
slice and deep pages are stable.

`docs/system-design.md` has the full design; `docs/prd.md` has the product framing.

## The taxonomy, and what it costs to change

The closed vocabulary of document types and KYC purposes lives in one file:

```
src/main/resources/taxonomy/taxonomy.yaml
```

It is the one part of this system a reader is expected to edit. A type entry carries a label, its
default purposes, the title and content patterns that identify it, and the query synonyms that reach
it.

**Adding a type means touching four things:**

1. The type entry in `taxonomy.yaml`.
2. The `version` at the top of that file. Raising it is what triggers reclassification.
3. `src/test/resources/eval/classification.json`, which asserts the expected type of every seed
   document and must stay at 100%.
4. The evaluation queries, if the new type should answer a question that is measured.

**Reclassification runs at startup.** Rows written under an older taxonomy version are re-labelled in
batches of 100 — rule-classified rows are re-run, rows whose type came from a request keep it, and
every row gets fresh label text and a fresh label chunk. It is not blocking: readiness does not wait
for it, so the service answers requests while it proceeds. A row that has not been reached yet is
searchable under its *old* labels, so the window is briefly stale, never absent, and an interrupted
pass simply resumes on the next start.

## Design decisions

**Embeddings run in-process, not through a hosted API.** The model (`all-MiniLM-L6-v2`, ONNX) ships
inside the jar, so a clean clone searches with no key, no network and no per-request cost, and
document text never leaves the process on the mandatory path. The costs are real: a larger image, a
384-dimension model rather than a stronger hosted one, and CPU-bound embedding on write. For a corpus
this size that trade is clearly worth it, and the evidence that a bigger model would not have helped
is in the table at the top.

**Results are ordered by type and provenance, not by a fused score.** A trigram similarity and a
cosine distance are not comparable quantities, and normalising them into one number would invent a
weight nobody could defend. Instead the plan's shape picks a tier order: naming a person is the
highest-precision signal in the system, so identity hits lead; a description match is weak evidence,
so it ranks below the documents it would otherwise hide. The ordering is a pure function, which is why
it is unit-tested without a database and why pagination is a slice.

**Documents are classified by rules, not by an LLM at ingest.** Rules are deterministic, add no
latency to `POST`, need no credential, and are exhaustively testable — the classifier is asserted
against every seed document. An LLM at ingest would put a network call and an API key on the write
path, which would end the zero-credential local run and make `201` depend on a third party. KYC
document types are a small, stable vocabulary, which is exactly where rules win. Documents the rules
cannot place become `unknown` rather than a guess, and classifying those asynchronously is a
documented follow-up.

**The database is Postgres alone.** Trigram, full-text and vector search are three extensions of one
store, which keeps a document and its embeddings in a single transaction. That is what makes a
document searchable the moment `201` returns.

## Cut on purpose

| Cut | Why |
|---|---|
| **Deployment** | Nothing is deployed. The GCP shape is designed and written down, but a reviewer's time is better spent on `docker compose up` than on my Cloud Run bill. Migrations and the summary lease are already written so more than one instance would be correct. |
| **Load testing** | No benchmark was run, so no measured latency is claimed (see below). Per-stage timers are in place so the numbers can be taken in operation, which is the only place they would mean anything. |
| **Multi-tenancy** | No tenant column, no row-level security. It changes every query and every index, and the brief describes one advisor's view. The migration path is noted in the design. |
| **An ANN index** | Exact scan over roughly 4×10⁴ chunks. HNSW is worth adding when the scan exceeds its budget and not before; at this corpus size it would only add a parameter to tune. |
| **Update and delete endpoints** | The brief describes create and search. Reclassification is the one internal write to an existing row, and it only changes labels. |
| **Authentication beyond a static key** | One shared key in a header. Real auth is an identity provider and a session model, which is a different assignment. |

## Deviations from the brief

**Endpoints added.** The brief specifies `POST /clients`, `POST /clients/{id}/documents` and
`GET /search`. This also serves `GET /clients`, `GET /clients/{id}`,
`GET /clients/{id}/documents`, `GET /clients/{id}/documents/{documentId}`,
`POST /clients/{id}/documents/{documentId}/summary`, `GET /health`, the OpenAPI document and Swagger
UI, and a React SPA at `/`. The reads exist so a reviewer can confirm what was written without opening
a database, and the SPA exists because search is easier to judge through a search box.

**Search results are a union, not the brief's `Document`.** A result is tagged `client` or `document`
and carries a `match` object (`field` and `tier` for a client; `passage`, `signals` and `labels` for a
document). The brief invites extending the model where needed, and without provenance a ranking cannot
be argued with.

**Document results omit `content`.** The brief's `Document` schema includes it. A search result
returns the matching passage instead, and the full text is one `GET` away. Returning 126 full
documents in a result page would be a bandwidth decision disguised as a schema decision.

**The embedding input is not the raw content.** Each body chunk is embedded as
`title + "\n\n" + chunk text`, so every chunk carries its document's title, and each document gets one
extra synthetic *label chunk* embedded from `title + "\n" + label text`. The stored vectors therefore
do not correspond one-to-one with spans of the original text, though the passages returned to a client
always do.

**Pagination and an API key** were added: `limit`/`offset` with `X-Total-Count`, and `X-API-Key` on
every data endpoint. Health, the OpenAPI document, Swagger UI and the SPA's own static assets
(`/`, `/index.html`, `/assets/*`, `.js`, `.css`, images) are exempt, because a browser cannot attach
a header when fetching them.

## Known limits

**The semantic floor is a coarse gate, not a tuned threshold.** It is currently `0.238`, derived by
the evaluation as the midpoint between the lowest true positive and the highest true negative. It does
not cleanly separate near-domain noise from genuine paraphrase: measured on this corpus,
`cheap hotel deals in Rome` scores 0.328 against a lease and `weekend flight to Lisbon` 0.297 against a
passport, while a real paraphrase such as `evidence of where the client lives` scores as low as 0.12
against one electricity bill. Those ranges overlap, so raising the floor would drop real answers before
it dropped travel noise. Such matches are admitted by the semantic signal alone and rank below every
label and lexical hit. The remedies are a reranker or intent prototypes, not a better number.

**A lexical false positive.** `weather forecast for the weekend` returns the client *Zoë Fairweather*
at 0.75, because trigram similarity finds `weather` inside her email address. It is a real consequence
of the 0.6 lexical floor that makes `Hendersen → Henderson` work at 0.70.

**Short-name typos.** `jhon` (0.20) and `joe` (0.50) against "John Doe" cannot clear any floor that
keeps the rest of the corpus correct. Levenshtein behind a length limit is the follow-up.

Queries of one or two characters, non-English synonyms and non-English stemming are all out of scope.

## Performance

**These are estimates, not measurements. No load test was run and none of these figures is
benchmarked.** They are budget arithmetic from the stages the timers cover, recorded so the design can
be argued about; the real numbers would come from the Micrometer timers in operation.

A search is estimated at **about 45 to 110 ms** against a p99 budget of 300 ms, dominated by the
semantic scan at 25 to 75 ms. Document creation is embedding plus one transaction: an estimated 10 to
50 ms typically, and up to about a second for a document at the 64 000-character cap, because cost is
linear in length. The per-stage breakdown lives in
[the system design](docs/system-design.md#performance-and-capacity) so the figures have one home
rather than two.

## Tests

```bash
./gradlew check          # format, compile, unit and integration tests
```

Integration tests need Docker; they run the real schema on `pgvector/pgvector:pg17` through
Testcontainers and the real embedding model, not stubs.

Relevance is guarded by an evaluation set rather than by taste. It holds **34 queries**: 26 declare a
positive expectation (an exact first result, full recall within *n* positions, or a compound
ordering) and 8 are negatives that must return nothing. Every document query also asserts that no
client outranks an expected document. The last run logged:

```
eval summary queries=34 MRR=1.0 meanRecall@n=0.9615384615384616
```

It also re-derives the semantic floor and fails the build if the margin it depends on disappears. The
four failures in the table at the top of this file are all in it.

## Layout

```
src/main/java/…/onboarding/   write side: clients, documents, classification, chunking, summaries
src/main/java/…/search/       read side: planning, retrieval, fusion, ordering, hydration
src/main/java/…/shared/       embedding model, taxonomy, web plumbing
src/main/resources/taxonomy/  the vocabulary you are expected to edit
frontend/                     React SPA, built into the jar
http/                         runnable requests for every endpoint
docs/                         system design, PRD, the original brief
```
