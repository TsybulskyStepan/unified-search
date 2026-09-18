# 08 — Pin the floors with the eval set against the live endpoint

**What to build:** The guard that keeps relevance from regressing silently. The eval set from ticket
03 moves from raw similarity scores to real calls against the search endpoint, so it measures what an
advisor actually receives rather than what the model scores internally.

It asserts in both directions: every expected query returns its document near the top, every unrelated
query returns nothing, and — the assertion that matters most — every document-shaped query returns no
clients at all.

That last one is what pins the lexical floor. Because clients rank above documents unconditionally, a
floor set slightly too low breaks the brief's second example while every presence-only assertion still
passes. The two floors are therefore handled differently: the semantic floor is *derived* from the
measured gap, since cosine similarity has no principled default; the lexical floor is *fixed* at the
trigram default and merely guarded. A midpoint rule applied to the lexical floor would compute a lower
number and admit exactly the weak client matches that break things.

**Blocked by:** 07 — Add documents to search.

**Status:** ready-for-agent

- [ ] The eval runs through the search endpoint rather than against similarity scores directly
- [ ] Every positive pair returns its expected document within the top three, and MRR is logged
- [ ] Every negative query returns no documents
- [ ] Every document-shaped query returns zero clients
- [ ] The build fails if the gap between the lowest positive and the highest negative closes
- [ ] The identifier-probe query is present and its outcome is recorded, whether or not it passes
- [ ] The lexical floor is asserted from both sides: client positives clear it, and document queries return no clients
