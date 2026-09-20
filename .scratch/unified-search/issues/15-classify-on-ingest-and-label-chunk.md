# 15 — Classify on ingest: type, purposes, and the label chunk

**What to build:** Every document learns what it is and which KYC question it answers, at the moment
it is created, by rule — no model call, no credential, no added latency on `POST` (§3.3, §1.4). A
reviewer with a clean clone and no API key still gets a fully labelled corpus.

Three pieces ship together because none of them is useful alone: the columns that hold the labels
(§2.1), the classifier that fills them (§3.3), and the label chunk — one extra vector per document
whose text says what the document *is* rather than what it *says* (§5.3). The columns are what make
the label retriever possible in 17. The label chunk is what catches the paraphrase the synonym list
does not cover, and it is immune to the chunk-dilution failure that buried `source of funds` in a
window full of GBP figures.

Then the part that is easy to defer and expensive to defer: rows that already exist. The taxonomy
will change — that is the point of putting it in a file — and a document classified under version 1
must not sit in the index answering version 2's questions. `Reclassifier` (§3.4) is what keeps stored
labels and query intents speaking the same language, and `taxonomy_version` on the row is what makes
"which rows are stale" a query rather than a guess.

**Blocked by:** 14 — The taxonomy.

**Status:** ready-for-agent

- [ ] A created document is stored with its type, purposes, classification source and taxonomy version
- [ ] Classification is deterministic: identical title and content always yield identical labels
- [ ] A request may name the type, and optionally its purposes, and the stored source records that it did
- [ ] An unrecognised document is stored as `unknown` with no purposes and is still searchable
- [ ] A tie between two candidate types yields `unknown` rather than an arbitrary winner
- [ ] Every document in the seed corpus classifies to its expected type, from a checked-in expectations file
- [ ] The document row, its label chunk and its body chunks still commit in one transaction
- [ ] A returned passage is always body text and never label text
- [ ] Rows below the current taxonomy version are brought up to date at startup, in batches, safe to interrupt
- [ ] Readiness does not wait for reclassification, and a stale row stays searchable under its old labels meanwhile
- [ ] Reclassification keeps a request-supplied type while refreshing that row's labels and version
- [ ] Reclassification runs before the seeder, so a freshly seeded document is never immediately stale
- [ ] `POST` still makes no network call and still needs no credential
