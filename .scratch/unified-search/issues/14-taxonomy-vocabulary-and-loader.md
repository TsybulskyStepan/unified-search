# 14 — The taxonomy: a closed vocabulary for what a document is for

**What to build:** The signal v1 never had. v1 could compare a query to a document's *words* two ways,
trigram and embedding, and both are surface similarity. Neither knows that a council tax bill and a
utility bill answer the same KYC question, or that "tax residency" and "council tax" are different
questions that happen to share a word. §0.1 traces all four eval failures back to that one gap.

This ticket ships the vocabulary and nothing else: the file, the types and purposes it names, and the
code that loads and validates it (§3.1, §3.2). No classification, no retrieval, no behaviour change.
It goes first because everything else in §14 reads from it.

The file is a contract between two modules that never import each other (§1.3): the classifier writes
labels from it, the planner reads intents from it. That is why validation is the substance of this
ticket rather than a detail of it. A purpose no type references, or a synonym that collides with an
id, splits those two sides apart quietly — the classifier keeps tagging and the planner keeps
matching, and they stop meaning the same thing. Failing at startup is the only place that costs
nothing.

**Blocked by:** nothing.

**Status:** ready-for-agent

- [ ] The taxonomy loads once at startup from one file and is reachable from both modules through `shared`
- [ ] Startup fails loudly on a duplicate type or purpose id
- [ ] Startup fails on a type naming a purpose that does not exist
- [ ] Startup fails on a synonym that collides with a type or purpose id
- [ ] Every type in §3.1 is present with its label, default purposes, title patterns, content patterns and synonyms
- [ ] Every purpose in §3.1 is present with its label and query synonyms
- [ ] The file carries a version, and that version is readable by the code that will stamp documents
- [ ] `unknown` is a legal type carrying no purposes
- [ ] Adding a type is a change to the file alone, with no code change required
- [ ] Nothing reads the taxonomy yet: search and ingest behave exactly as before
