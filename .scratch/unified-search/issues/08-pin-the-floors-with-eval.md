# 08 — Pin the floors with the eval set against the live endpoint

**What to build:** The guard that keeps relevance from regressing silently. The eval set from ticket
03 moves from raw similarity scores to real calls against the search endpoint, so it measures what an
advisor actually receives rather than what the model scores internally.

It asserts in both directions: every query's full set of genuinely relevant documents is labelled, and
that labelled set must occupy the top of the ranking as a block — recall@N with a hard purity
constraint, where N is the labelled set's size, so a single-answer query becomes rank-1-or-fail. Every
unrelated query returns nothing, and — the assertion that matters most — every document-shaped query
returns no clients at all.

That last one is what pins the lexical floor. Because clients rank above documents unconditionally, a
floor set slightly too low breaks the brief's second example while every presence-only assertion still
passes. The two floors are therefore handled differently: the semantic floor is *derived* from the
measured gap, since cosine similarity has no principled default; the lexical floor is *fixed* at the
trigram default and merely guarded. A midpoint rule applied to the lexical floor would compute a lower
number and admit exactly the weak client matches that break things.

Labelling as a set, not a single "expected document," is what keeps this gate compatible with the corpus
duplication below: when the same artifact type exists for more than one client, every genuine match is
in the labelled set and the purity assertion still holds — a duplicated positive grows N, it doesn't
break the gate. If a query's labelled set grows large enough to feel like a blunt discriminator, retarget
which artifact type gets duplicated — e.g. Investment Policy Statement across clients, rather than the
proof-of-address documents a content query already retrieves — instead of relaxing the assertion.

**Blocked by:** 07a — Client mention detection and compound-query ordering.

**Status:** ready-for-agent

- [ ] The eval runs through the search endpoint rather than against similarity scores directly
- [ ] Every query's relevant documents are fully labelled, and the labelled set occupies ranks 1..N (N = its size) with nothing unlabelled above or inside it; MRR is logged from the same labels, so single-answer queries (W-9, trust restructuring, risk tolerance) are rank-1-or-fail
- [ ] Every negative query returns no documents
- [ ] Every document-shaped query returns zero clients
- [ ] The build fails if the gap between the lowest positive and the highest negative closes
- [ ] The identifier-probe query is present and its outcome is recorded, whether or not it passes
- [ ] The lexical floor is asserted from both sides: client positives clear it, and document queries return no clients
- [ ] Compound queries assert **order**, not just membership: the named client's document first, that client second
- [ ] The corpus holds the same artifact type for more than one client, so a compound assertion can actually discriminate
- [ ] Every name-only query still returns the client first — the guard that keeps the brief's first example from silently becoming a document result
- [ ] A generic category query does not reorder around a client whose first name is an ordinary word
- [ ] The mention threshold is guarded from both sides: a misspelled name still scopes, a category word never does
