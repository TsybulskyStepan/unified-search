# 12 — Search observability: timers and the no-PII audit line

**What to build:** Enough signal to answer "is it slow, and where" without a load test — this
instrumentation stands in place of the benchmark that was deliberately cut, and is the same hook a
real deployment would use. Each search stage is timed, document embedding and summary calls are timed,
and summary outcomes are counted by terminal status.

The audit line is the constraint that makes this more than plumbing. Search activity is worth
recording, but a query in this system is routinely a client's name or email address. So the line
records *shape*, never content: query length, hit counts per retriever, how many results were
returned, per-stage timings. The same rule governs the write path and the worker — identifiers, counts
and durations only.

This is the one ticket in the set that is a horizontal slice rather than a tracer bullet. It earns
that because the no-PII rule is a stated invariant with no natural home in any feature ticket, and
invariants that belong to everyone belong to no one.

**Blocked by:** 07 — Add documents to search.

**Status:** ready-for-agent

- [ ] Timers exist for the lexical query, the query embedding, the semantic query, total search, document embedding and summary calls
- [ ] Summary outcomes are counted by terminal status
- [ ] Each search emits one audit line carrying its request id, query length, per-retriever hit counts, returned count and stage timings
- [ ] A test asserts that query text, names, emails, titles and document content never appear in log output, across the search, write and worker paths
- [ ] Metrics are reachable for inspection on a running instance
