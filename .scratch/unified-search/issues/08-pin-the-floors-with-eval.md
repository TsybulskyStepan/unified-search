# 08 — The relevance eval: expectation shapes, the floors, and the no-client-above guard

**What to build:** The guard that keeps relevance from regressing silently, rebuilt around what v2
actually promises. It runs against the live `/search` endpoint, so it measures what an advisor
receives rather than what the model scores internally.

v1's gate had two defects that hid the real picture (§0.1). A "top three" assertion cannot pass for a
query with seven correct answers, and a single compound test query cannot tell a working rule from a
lucky one. The first defect is already corrected — the labels are complete and the assertion is a
recall@n with a purity constraint — and this ticket generalises that into the four expectation shapes
in §11.3.

The addition that matters most is the **no client above any expected document** guard. v1 asserted
"zero clients" for document queries, which was the right instinct expressed too narrowly: it could
not express `advisory fees`, where the correct answer is that Grace Kim may appear, but under the two
engagement letters rather than over them. The general form is the one worth having.

The floors stay derived rather than guessed. `semanticFloor` is still the midpoint of the measured
gap and the build still fails if that gap closes. What changes is its meaning: with label and lexical
admission it is a recall gate rather than the only door into the result list (§6.3), so a document
below it is now a ranking question instead of a disappearance.

**Blocked by:** 18 — Fuse the signals, then order by plan shape.

**Status:** ready-for-agent

- [x] Every genuinely relevant document for a query is labelled, so a query with several correct answers can be asserted at all
- [x] A total miss lowers MRR instead of raising it
- [x] Every query is measured and recorded whether or not it passes, so one failure does not hide the rest
- [ ] Each query declares one expectation shape, and the shape decides its assertion
- [ ] A single-answer query asserts position 1
- [ ] An n-answer query asserts every expected item inside the first n positions
- [ ] A compound query asserts the expected document first and the expected client second
- [ ] An out-of-domain query asserts an empty result
- [ ] No client outranks any expected document, on every document query
- [ ] Recall@n and MRR are logged on every run
- [ ] The semantic floor is re-derived from the measured gap, and the build fails if the gap closes
- [ ] The lexical floor is guarded from both sides and is never derived from the eval
- [ ] The classifier reaches 100% against the expected-type file
- [ ] Compound behaviour is covered by more than one query, including a possessive and a bare-category case
- [ ] Every failure listed in §0.1 passes
