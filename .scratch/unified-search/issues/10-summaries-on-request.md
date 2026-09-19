# 10 — Summaries on request: endpoint, durable queue, lease

**What to build:** Summaries are generated only when a human asks for one. The request endpoint
accepts and returns immediately, which means something durable has to own the promise — the document
row itself becomes the queue, surviving a restart where an in-process background task would strand the
caller polling a row that will never change.

A worker claims pending rows under a short lease, incrementing the attempt counter *at claim time*, so
a crash mid-call still consumes an attempt and a poison document cannot loop forever. A nudge from the
request handler covers the fast path; a periodic sweep recovers anything the nudge lost to a restart
and doubles as retry backoff.

Repeated requests must be safe: asking twice cannot double-enqueue or reset a job already in flight.
And reading a document must never trigger generation — a read that quietly spends money is a trap for
any client that polls.

Build this against a test double. The real model arrives in ticket 11.

**Blocked by:** 05 — Create and fetch documents.

**Status:** complete

- [x] Requesting a summary for a document that has none returns 202 and moves it to pending
- [x] A second request while pending returns 202 and causes no additional generation call
- [x] A request for a document that is already summarised returns 200 and causes no call
- [x] Reading a document repeatedly leaves its state untouched and never calls the summarizer
- [x] A claimed row carries a lease and an incremented attempt count, and two concurrent claims never return the same row
- [x] A row whose lease expires becomes claimable again
- [x] Transient failures are retried until the attempt limit is reached, after which the row is marked failed
- [x] Requesting again after a failure returns the row to pending with attempts reset, and it can then succeed
- [x] The document remains searchable throughout every one of these states
