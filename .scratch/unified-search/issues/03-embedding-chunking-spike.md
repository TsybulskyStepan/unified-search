# 03 — Embedding and chunking spike, with the eval set as the gate

**What to build:** The risk gate. Prove the embedding model can bridge a KYC *category* to a document
*artifact* — "address proof" to a utility bill, with no shared words — before anything is built on
top of that assumption.

Build the embedder, loaded in-process so document content never leaves the trust boundary, warmed at
startup, and exposing the model identifier that later gets stored against every chunk. Build the
chunker: fixed word windows on an overlapping stride, carrying code-point offsets into the content
rather than copies of the text. Build the realistic KYC corpus and its query set — which doubles as
the seed corpus, so the demo and the eval can never drift apart — and a test that embeds the corpus
and reports the similarity gap between the expected pairs and the unrelated queries.

The deliverable here is a number, not a feature: the chosen semantic floor, with the measured gap
that justifies it. If positives and negatives do not separate, stop and change the model before
continuing — tickets 05 through 08 all rest on this working.

**Blocked by:** 01 — Walking skeleton.

**Status:** ready-for-agent

- [ ] The embedder loads in-process with no network access at first use, warms during startup, and exposes its model identifier
- [ ] The chunker splits on the §5.3 geometry, preserves code-point offsets, yields a single chunk for short content, and handles surrogate pairs correctly
- [ ] A test asserts every chunk's embedding input stays under the model's word-piece limit, using the model's own tokenizer rather than an estimate
- [ ] The corpus covers the §12.3 document set across roughly eight clients, with the profile descriptions and social links the client cases need
- [ ] A test embeds the corpus, scores the positive pairs and the negative queries, logs the gap, and fails if the two sets overlap
- [ ] The chosen semantic floor is recorded in configuration with the measured gap stated alongside it
