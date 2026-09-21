# 13 — README and the worked walkthrough

**What to build:** The document a reviewer reads first. It has to get someone from a clean clone to a
working search in one command with no credentials, then show the system doing the two core things —
with real requests and real responses, not prose claiming they work.

It also has to carry the decisions, because a trade-off nobody wrote down cannot be judged. Why embeddings run in-process rather than through a hosted API. Why
results are ordered by type rather than fused. And what was cut on purpose — deployment, the frontend,
the load test, multi-tenancy — each with the reason it was cut, so a deliberate omission is not read
as an oversight.

Latency figures appear as estimates, labelled as estimates. Publishing an unmeasured number as though
it were a measurement would be worse than publishing no number at all.

**Blocked by:** 08 — Pin the floors; 09 — Seed the demo corpus; 11 — Gemini summarizer; 12 — Search observability.

**Status:** complete

- [x] Setup is one command from a clean clone with zero credentials, verified on a machine with no prior state
- [x] The optional variable that enables summaries is documented, along with what happens when it is absent
- [x] The two core examples (J1, J2) and the fuzzy-name case appear as copy-pasteable requests with their actual responses
- [x] The summary lifecycle is demonstrated as two requests and the states between them
- [x] Design decisions and deliberate cuts are stated with their reasons
- [x] Extensions beyond the core API are listed, including the additional endpoints and the embedding input
- [x] Summary egress to a third party, and how to disable it, are stated plainly
- [x] Latency figures are labelled as estimates and say explicitly that they were not benchmarked

v2 additions (§14.7). The taxonomy is the one part of this system a reader is expected to edit, so
the README has to say where it lives and what editing it costs.

- [x] An identity query, a category query and a compound query each appear as a real request with its real response
- [x] The taxonomy file is named as the place to add a document type, with what else has to change when one is added
- [x] Reclassification on startup is described, including the briefly-stale window and why it is not blocking
- [x] The rule-based classifier is explained as a deliberate choice over an LLM at ingest, with the zero-credential run as the reason

Added after the pre-README assessment (2026-09-20), each from a finding made on a running stack.

- [x] The reset command (`docker compose down -v`) is stated next to the setup command, with the symptom it fixes: the seeder skips a non-empty database, so one client created before the first seed leaves `NevisWealth` returning `[]`
- [x] The complexity is justified by the four failures it fixes (§0.1), in a paragraph a reviewer meets before the architecture, because the structure is heavier than a plain embedding wrapper
- [x] The semantic floor is described as a coarse gate with its measured limit (§13), not as a tuned threshold
- [x] The broadband-bill gap (§11.3) and the `Fairweather` lexical false positive are listed among the known limits
- [x] `http/` is named as the place to run every endpoint, with the reset command
