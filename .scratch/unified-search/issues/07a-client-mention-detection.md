# 07a — Client mention detection and compound-query ordering

**What to build:** An advisor can type a client's name and what they want from them in one box —
`"doe utility bill"` — and get **that client's** utility bill first, the client second, and everyone
else's utility bills after. Today this query returns neither: the client is dropped because the extra
words dilute the match below the floor, and the documents are never scoped to anyone, so the right
bill surfaces only by luck.

Two pieces, and neither is demoable without the other:

**Recognising the client.** The existing client match asks "is this query about this client?". A
compound query needs the other question — "is this client named *inside* this query?" — which is
answered by comparing the query's individual terms against client identity rather than the query as a
whole. A name term scores perfectly against its client; category terms like `utility` and `bill`
score zero against any name, which is what makes the signal safe to act on.

**Acting on it.** The named client's above-floor documents move to the front, followed by the client,
then the rest in the usual order. Three rules keep this from breaking the brief's own examples, and
all three need tests, not just code:

- It **partitions, never filters** — only documents that already cleared the semantic floor are
  reordered, so a wrong guess costs ordering, never recall.
- **One named client, or none** — an ambiguous query falls back to today's behaviour.
- **A residual is required** — some term must not have matched the client. This is the rule that
  protects J1: a firm name matches a client's email perfectly, so without it, that client's documents
  would outrank the client and the brief's first example would fail.

**Blocked by:** 07 — Add documents to search.

**Status:** complete

- [x] A query naming a client plus a category returns that client's matching document first and the client second
- [x] Other clients' documents of the same type still appear, below those
- [x] A query that is only a client name is unaffected: the client is first, as it is today
- [x] A query naming no client is unaffected
- [x] A query naming two clients promotes neither one's documents
- [x] A named client with no above-floor documents falls back to client-first
- [x] A misspelled client name still scopes, so the feature is not limited to advisors who type accurately
- [x] A category term that happens to be someone's first name does not remove or hide any document — at worst it reorders documents that already qualified
- [x] Scoping never changes which documents are returned, only their order
- [x] Ordering is unit-testable from plain lists, with both branches and all three fallbacks covered
- [x] Paging a compound query stays consistent: the branch is decided once per query, not per page
- [x] No schema change
