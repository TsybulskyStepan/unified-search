# 13 — README and the graded-artifact walkthrough

**What to build:** The document a reviewer reads first. It has to get someone from a clean clone to a
working search in one command with no credentials, then show the system doing the two things the brief
actually asked for — with real requests and real responses, not prose claiming they work.

It also has to carry the decisions, because the brief grades trade-offs and an undocumented decision
scores nothing on that axis. Why embeddings run in-process rather than through a hosted API. Why
results are ordered by type rather than fused. And what was cut on purpose — deployment, the frontend,
the load test, multi-tenancy — each with the reason it was cut, so a deliberate omission is not read
as an oversight.

Latency figures appear as estimates, labelled as estimates. Publishing an unmeasured number as though
it were a measurement would be worse than publishing no number at all.

**Blocked by:** 08 — Pin the floors; 09 — Seed the demo corpus; 11 — Gemini summarizer; 12 — Search observability.

**Status:** ready-for-agent

- [ ] Setup is one command from a clean clone with zero credentials, verified on a machine with no prior state
- [ ] The optional variable that enables summaries is documented, along with what happens when it is absent
- [ ] The brief's two examples and the fuzzy-name case appear as copy-pasteable requests with their actual responses
- [ ] The summary lifecycle is demonstrated as two requests and the states between them
- [ ] Design decisions and deliberate cuts are stated with their reasons
- [ ] Deviations from the brief are listed, including the additional endpoints and the embedding input
- [ ] Summary egress to a third party, and how to disable it, are stated plainly
- [ ] Latency figures are labelled as estimates and say explicitly that they were not benchmarked
