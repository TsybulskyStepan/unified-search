# 16 — The query planner: parse once, before any retrieval

**What to build:** v1 handed the raw query to each retriever and let each one interpret it alone. v2
parses it once, up front, into a plan — who was named, what text is left, and which taxonomy labels
that remainder refers to (§6.1) — and every retriever and the ordering read that one plan.

The parse is where two v1 bugs die. `John's bill` matched nothing, because nothing ever stripped the
possessive. And `bill` could be read as Bill Carter, so a plain category query got reordered around a
person who had nothing to do with it. The ambiguity rule (§6.1) is the fix, and it has to hold an
edge on both sides: `bill` is a category, while `bill's` and `bill carter` are a person. A rule that
only ever suppresses is as wrong as one that only ever fires.

Mention detection itself is not reopened. The leading-run anchor already ships and already works
(07a); this ticket moves it behind the plan and gives it the ambiguity rule and a normalisation step
it never had.

**Blocked by:** 14 — The taxonomy.

**Status:** complete

- [x] The query is normalised before anything reads it: case, Unicode form, possessives, whitespace, and hyphenated forms such as `w-9`
- [x] The plan carries at most one mentioned client, the residual text, and the set of matched intents
- [x] A possessive client name scopes the query
- [x] A bare category word that is also a client's first name does not scope the query
- [x] That same word scopes the query when written as a possessive, or alongside a surname that matches the same client
- [x] Two clients matching means no mention, and neither one's documents are promoted
- [x] A name-only query leaves an empty residual
- [x] Intent matching takes the longest phrase, so a multi-word type name is not shadowed by a single word inside it
- [x] A query matching no intent still searches, on the remaining signals
- [x] The planner is a pure function, tested from a table covering every worked example in §6.5
- [x] A query that scoped to a client before this ticket still scopes to that client after it
