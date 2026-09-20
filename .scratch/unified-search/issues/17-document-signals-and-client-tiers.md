# 17 — Three document signals, and two tiers for clients

**What to build:** The retrieval half of the fix. Documents stop depending on one embedding and gain
two more ways in (§6.3): a **label** match, which finds a document for what it is *for* however it
happens to be worded, and a **lexical** match over title, labels and content. Clients gain a
distinction they never had (§6.2) — being *named* versus merely being *described*.

The label retriever is what makes `proof of address` return all seven. Samuel's bill and statement
never say "occupancy" or "residency", and no embedding rescues a document that simply does not
contain the words; v1 could only ever find the documents that happened to be written in
proof-of-address language. The client tiering is the other half of §0.1's fourth failure: Grace Kim
outranked two engagement letters because her description mentions "advisory arrangements" and v1 put
every client above every document. Identity still beats documents. Context no longer does.

Each retriever is separately testable and separately failable, and a failure is an error, not a
smaller result set (§6.1). A half-populated list silently missing one signal is the worst outcome
available here, because nothing in the response would show what went missing.

**Blocked by:** 15 — Classify on ingest; 16 — The query planner.

**Status:** ready-for-agent

- [ ] A document carrying a queried purpose is admitted however its text is worded
- [ ] A document matching on stemmed text in its title, labels or content is admitted
- [ ] The semantic retriever considers the label chunk alongside the body chunks
- [ ] A query whose residual is empty returns no documents at all
- [ ] A residual of only stop words neither errors nor admits everything
- [ ] A client matched on name, email or social links is marked identity; one matched only on description is marked context
- [ ] The tier a client matched on is visible in the response
- [ ] Each retriever caps its candidates, and the cap is the same for all three
- [ ] Any one retriever failing yields an RFC 9457 error and never a partial result
- [ ] The three retrievers and the query embedding run concurrently, so the request is not the sum of their latencies
- [ ] The semantic floor still applies, but a document below it can now be admitted by another signal
