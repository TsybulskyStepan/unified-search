# 07 — Add documents to search: semantic retrieval, type ordering, passage hydration

**What to build:** The other half — documents join the same ranked list. The query is embedded and
compared against chunk vectors; a document scores as its best chunk, and that chunk becomes the
passage shown to the advisor. Only vectors produced by the running model are considered, so a model
change makes documents disappear loudly instead of returning plausible nonsense.

Clients are placed above documents. The two scores are not comparable and never will be, and
pretending otherwise by fusing them would dress an arbitrary tie-break in a principled-sounding name.
The two retrievers run concurrently and share nothing; if either fails, the whole search fails, because
half a corpus returned silently is indistinguishable from "no such document".

This is where the brief's second example becomes an executable test — and it asserts **zero clients**,
because a single weak client match would take position one and demote the utility bill beneath it.

**Blocked by:** 05 — Create and fetch documents; 06 — Search clients lexically.

**Status:** complete

- [x] Searching for a document category returns the artifact that satisfies it, with a passage, and returns zero clients
- [x] A document scores as its best chunk, and the passage returned is that chunk's span of the content
- [x] Full document content never leaves the database for a search; results omit it and carry the owning client's name instead
- [x] A query matching both types returns the client first, even when the document's similarity is numerically higher
- [x] Chunks written under a different model identifier are invisible to search, and the document returns no semantic hit rather than a wrong one
- [x] A document created moments earlier is found with no wait and no retry
- [x] The two retrievers run concurrently, and either one failing returns a server error rather than a partial list
- [x] Documents below the semantic floor are excluded
