# 18 — Fuse the signals, then order by plan shape

**What to build:** Three document signals now exist and have to become one list, and the result list
has to stop being "every client, then every document" regardless of what was asked.

Fusion is reciprocal rank fusion over the two *ranked* signals, with label admission carried as a
tier flag rather than folded into the sum (§6.4). "Is tagged proof of address" is boolean evidence,
and mixing boolean evidence into a weighted score requires inventing a weight nobody can defend
afterwards. Ordering becomes one of two shapes chosen by the plan (§6.5): a category or free-text
query puts identity clients, then documents, then context clients; a query naming a client puts that
client's documents first and the client second, because there the client is the qualifier and the
document is the target.

No score is ever compared across types — which is precisely why this is a tier order and not a sort.
A `word_similarity` of 0.64 and a fused document score of 0.03 are not two numbers on one scale, and
v1's bug was treating them as though position could be argued from them.

The response has to explain itself too (§4.3): which signals admitted a document, which labels
matched, which tier a client matched on. A ranking nobody can explain from the response is a ranking
nobody can debug from a bug report.

**Blocked by:** 17 — Three document signals.

**Status:** ready-for-agent

- [ ] A document admitted by more than one signal outranks one admitted by a single signal, all else equal
- [ ] A document admitted by label alone still precedes every untagged document
- [ ] Ordering is a pure function of the plan and the two client tiers, testable from plain lists
- [ ] A category query returns identity clients, then documents, then context clients
- [ ] A query naming a client returns that client's matching documents, then the client, then other clients' matching documents
- [ ] A context-tier client never appears above a document that answers the query
- [ ] The order is total, so a page is a slice and deep pages stay stable
- [ ] Each result reports which signals admitted it and which labels matched
- [ ] A passage still comes from a body chunk, never the label chunk
- [ ] Ordering never removes a document, only moves it
- [ ] Both shapes are decided once per query, so paging cannot change the shape mid-result
