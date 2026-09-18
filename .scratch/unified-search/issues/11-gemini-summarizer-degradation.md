# 11 — Gemini summarizer and its zero-credential degradation

**What to build:** The real model behind the worker, and the behaviour when it is absent.

With an API key set, a requested document gets a short factual summary within seconds. With no key —
the default, and what every reviewer running this locally will have — the summarizer fails
permanently and the document lands in the failed state within one nudge. That path is deliberately
*observable* rather than hidden: a reviewer with no credentials can still press the button and watch
the state machine work end to end, and supplying one environment variable turns the same path green.
The no-key, bad-key and revoked-key cases all travel the same code path.

Document content is untrusted input. The prompt treats it as data rather than instructions, the model
has no tools and no access beyond the single document, and the worst a prompt injection achieves is a
misleading summary of that one document.

**Blocked by:** 10 — Summaries on request.

**Status:** ready-for-agent

- [ ] With a key configured, a requested summary reaches the ready state and is stored as plain text
- [ ] With no key, a requested summary reaches the failed state within one nudge, by the same code path as a bad or revoked key
- [ ] Rate limits, server errors and timeouts are classified as transient; authentication and invalid-request failures as permanent
- [ ] The model call is bounded by a timeout comfortably shorter than the claim lease
- [ ] The system instruction treats document text as data, and output length is bounded
- [ ] Search results are unaffected in every summary state, including failure
- [ ] The key is never logged, never baked into the image, and never returned to a client
