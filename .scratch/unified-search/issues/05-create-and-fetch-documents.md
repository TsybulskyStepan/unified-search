# 05 — Create and fetch documents, chunked and embedded atomically

**What to build:** A document created under a client is searchable the moment creation returns.
Creation chunks the content, embeds every chunk *outside* the transaction so model inference never
holds a connection open, then commits the row and all of its chunks together.

That atomicity is the point of the ticket: a document that exists but cannot be found is the worst
outcome in this system, and it is silent. Every chunk records which model produced it, so a later
model change cannot quietly leave incomparable vectors in the table.

No summary work happens here. The document is created with no summary and the model is never called —
creation latency is embedding plus two inserts, with no LLM anywhere in the budget.

**Blocked by:** 03 — Embedding and chunking spike; 04 — Create and fetch clients.

**Status:** ready-for-agent

- [x] Creating a document returns 201 with a location header and the document, its summary status showing that none was requested
- [x] The document row and every one of its chunks commit in a single transaction; a failure part-way leaves neither behind
- [x] Every chunk records the identifier of the embedder that is actually running
- [x] Embedding completes before the transaction opens
- [x] Fetching a document by id returns it; an unknown client or document returns 404
- [x] A document whose only relevant sentence sits around word 1000 produces chunks that cover it, so it is retrievable later
- [x] Creating a document never calls the summarizer
