# WealthTech Domain Analysis

Context research for the Nevis search assignment. The conclusion that matters most is in §5: **the two examples in the brief are not arbitrary test cases — they encode two real advisor workflows**, and knowing which ones changes how the search should behave.

---

## 1. Who Nevis is

| | |
|---|---|
| Legal entity | Nevis Wealth Technology Ltd, London UK |
| Founded | 2024 |
| Founders | Mark Swan, Philipp Burda, Ivan Chalov — all former Revolut executives |
| Funding | $35M Series A led by **Sequoia**, with ICONIQ and Ribbit Capital; $40M total |
| Traction | Supports advisors managing **$50bn+ in client assets** at fast-growing US wealth firms |
| Named customers | United Capital, GC Wealth, Apollon Wealth Management, Dodds Wealth |
| Security posture | SOC 2 Type II; enterprise agreements with OpenAI and Anthropic; **"never uses your private data to train or update AI models"** |

**Product positioning:** "the first unified AI platform built for wealth management" — advisors' operational work end to end. Shipped capabilities:

1. Meeting summaries with contextual awareness
2. Automated task generation from client interactions
3. Smart meeting prep / client briefs
4. **AI Search — retrieval of client information across unified systems**
   *Likely query taxonomy* (Nevis does not publish query examples; this is inferred from §5's example decoding and general advisor-CRM search research — see §7):
   - **Organizational / entity lookup** — a firm or employer name, expected to surface every associated contact and document (the `"NevisWealth"` case; §5.1)
   - **Category-to-artifact retrieval** — a regulatory or task category, expected to surface the document instances that satisfy it (the `"address proof"` case; §5.2)
   - **Fuzzy person lookup** — partial or misspelled name/email, single-client precision needed
   - **Status / follow-up queries** — "clients not contacted recently," "compliance docs due for renewal" — filtering more than free-text search, but often entered as a search box query in practice
   - **Ad-hoc keyword recall** — an advisor half-remembering a document ("the one about the trust restructuring") with no idea which client or field it lives in

   The two brief examples are the first two categories; the assignment does not require the latter three, but they explain why the response is a single ranked list rather than a client-only or document-only endpoint — the advisor doesn't know in advance which category their query falls into.
5. Client-ready emails in the advisor's voice
6. Account opening automation (data collection, custodian submission)

> **Item 4 is this assignment.** The take-home is a scaled-down version of a feature Nevis actually ships. That raises the bar on search *quality* judgement specifically — this is the part of the product they know intimately and will review against lived experience.
>
> **What we actually know about how they built it: very little.** No engineering blog, no published architecture, no disclosed indexing or embedding approach. The ICONIQ funding writeup describes only the product outcome — "integrates data across CRM, portfolio reporting, planning, custodial, communication, and file storage tools, creating a unified data layer that becomes the firm's system of record" — not the mechanism. The one concrete technical signal is the enterprise agreements with **OpenAI and Anthropic**, which suggests their production stack likely calls hosted LLM/embedding APIs rather than running models in-process — the opposite of the locked local-embedding decision here (§7, row 1). That divergence is fine: it is driven by our reproducibility and compliance requirements (NFR-6, NFR-28), not by a belief that hosted APIs are the wrong architecture. It should be named as a deliberate deviation, not left implicit.

Their stated problem framing: **"advisors spend 80% of their time on admin."**

### 1.1 What the founder background implies

Three ex-Revolut executives, Sequoia-backed, 18 months old with $50bn AUM under service. That profile predicts a review culture that values: pragmatic engineering over ceremony, evidence over assertion, explicit tradeoff reasoning, and production-mindedness (migrations, tests, observability) at small scale. It also predicts low tolerance for a solution that works only on the happy path.

---

## 2. The industry problem being solved

### 2.1 Fragmentation is the root cause

An RIA's data lives across separate systems that do not talk to each other: CRM (Wealthbox, Redtail, Practifi), custodians (Schwab, Fidelity, Pershing — each with its own data model, field names, and account structures), portfolio reporting (Orion, Addepar, Black Diamond), planning tools (RightCapital, eMoney), document storage, and email.

The documented consequences:

- **Shadow data** — staff maintain "a graveyard of messy spreadsheets" as glue because the CRM won't talk to custodians.
- **Manual re-keying** — the most common source of both data errors and staff frustration; every copy creates two risks, human error and a gap in the audit trail.
- **Silent staleness** — a client's contact details get updated in the CRM but stay wrong in portfolio reporting.
- Integration and data access are "chronically underweighted in evaluations and almost always become the source of pain after implementation."

**Why this makes search the wedge product:** when data is scattered across ten systems, the single highest-leverage feature is not another system — it is one box that searches all of them. Search is how a unified platform proves its value on day one.

This is also why the brief insists on **one endpoint returning both clients and documents**. A mixed-entity result list is not an API-design curiosity; it is the product thesis in miniature. An advisor asking "Henderson" does not know or care whether the answer is a person record or a PDF.

### 2.2 Compliance shapes everything

RIAs operate under **SEC Rule 204-2** (Investment Advisers Act of 1940): books and records relating to recommendations, advice, fund movements, and performance must be retained **five years, the first two in an appropriate office**, in an *easily accessible* format. Electronic communications are explicitly in scope and are called out as one of the highest-risk recordkeeping categories.

Three direct consequences for a search system:

1. **Retention is legally mandated, so the corpus only grows.** Documents are never deleted on a normal path. Search has to stay fast over an append-only archive.
2. **"Easily accessible" is a regulatory standard, not a UX preference.** Retrieval quality has compliance weight — an SEC examination request that the firm cannot satisfy quickly is a finding.
3. **Data residency and model training are contractual matters.** Nevis advertises that client data never trains models, and holds SOC 2 Type II.

> **This validates the local-embedding decision independently of the reviewer-convenience argument.** Running the embedding model in-process means document content never leaves the trust boundary to reach a third-party inference API. That is a *compliance* argument, and it is materially stronger than the "no API key needed" argument currently in the requirements. It should lead the README's design-decisions section.

---

## 3. Market structure and scale

| Metric | Value |
|---|---|
| SEC-registered wealth-management RIA firms (US) | ~15,000 |
| Assets advised | ~$130 trillion |
| End clients | ~65 million |
| Concentration | 77% of RIA assets held by just 7% of firms |
| Projected advisor shortage by 2034 (McKinsey) | ~100,000 |

Two structural forces make advisor automation a necessity rather than a nicety:

- **The advisor shortage.** Upper-high-net-worth households have grown nine-fold since 2010 while advisor headcount shrinks. Capacity per advisor must rise, and software is the only lever.
- **The great wealth transfer.** Trillions passing to the next generation — and **81% of heirs plan to fire their parents' advisor**. Institutional memory of the relationship, instantly retrievable, is retention infrastructure.

**Implied deployment shape for Nevis:** B2B multi-tenant SaaS. Tenants are firms, not individuals; the 7%-hold-77% concentration means a handful of large enterprise tenants dominate. Per-firm data isolation is table stakes, and per-tenant corpus sizes vary by orders of magnitude.

---

## 4. Competitive landscape

**Direct competitors — AI notetakers becoming advisor operating systems.** This is the category Nevis is in, and it is consolidating fast:

- **Jump** — market-share leader; meeting prep, notes, follow-ups, compliance workflows
- **Zocks** — notes, client emails, form filling, CRM profile updates; distributed via Commonwealth Financial Network
- **Zeplyn** — meeting documentation tied to CRM follow-through

The pattern noted across 2026 industry coverage: these products "are morphing into full advisor operating systems, competing for the same advisor desktop." Nevis's "first *unified* platform" positioning is a direct claim against them.

**Incumbent platforms shipping defensive native AI:** Wealthbox, Altruist Hazel, Advisor360 Parrot AI, Nitrogen, Practifi.

**Adjacent infrastructure (not competitors, but the systems Nevis must search across):** Addepar, Orion, Envestnet, iCapital, SS&C Advent, Black Diamond, Vestmark, Canoe Intelligence, Conquest Planning.

**Strategic read:** the industry narrative is "the end of best-of-breed" — unified platforms are winning over assembled point solutions. Search across a unified corpus is precisely the capability that a bundle of point solutions structurally cannot match. It is Nevis's differentiator, not a checkbox feature.

---

## 5. Decoding the brief's two examples

This is the payoff of the research. Both examples map to named Nevis product capabilities.

### 5.1 `"NevisWealth"` → `john.doe@neviswealth.com`

This is **organizational lookup**. An advisor searching a firm or employer name to surface every contact associated with it — before a meeting with that organization, or when an introduction comes in from it. It maps to Nevis's *Smart Meeting Prep*.

The workflow consequence: matching the **domain segment of an email address** is the actual requirement, not incidental string matching. The same logic should extend to `social_links` (a LinkedIn company URL carries the same organizational signal), which is likely *why* `social_links` appears in the schema at all — the brief's field list is a hint.

**This reinforces the tokenization finding in FR-11:** emails and URLs must be decomposed into component terms. Not a workaround for Postgres tokenization quirks — a direct encoding of how advisors actually search.

### 5.2 `"address proof"` → documents containing `"utility bill"`

This is **KYC/AML document retrieval during account opening** — mapping directly to Nevis's *Account Opening Automation*.

In wealth onboarding, "proof of address" is a regulatory document *category* satisfied by any of several artifact types: utility bill, bank statement, council tax bill, tenancy agreement. Likewise "proof of identity" is satisfied by passport, driver's licence, or national ID card. The advisor thinks in **regulatory categories**; the documents are titled with **artifact types**. Semantic search bridges exactly that gap.

Two consequences for the implementation:

- The semantic requirement is **category-to-instance generalization**, not loose synonymy. This is a fair test for a general-purpose embedding model, and it suggests the seed corpus should contain a realistic KYC document set (utility bill, bank statement, passport scan summary, W-9, tax return, engagement letter) so the demo exercises the real case rather than a contrived one.
- Evaluating with a handful of *category → artifact* query pairs drawn from KYC vocabulary is a far more convincing demonstration than a single anecdote — and it is cheap to build.

---

## 6. How this class of system is typically built today

None of this is Nevis-specific — it's the 2026 state of the art for "search that must handle both exact identifiers and fuzzy meaning," which is exactly the shape of this problem. Relevant because it validates some locked decisions and challenges one.

### 7.1 Hybrid search is the default, not a refinement

Production systems facing this exact split — lexical precision for identifiers, semantic recall for meaning — converge on the same three-stage architecture:

1. **Sparse retrieval (BM25 / full-text / trigram)** over structured and short fields — handles emails, IDs, exact names, SKUs, error codes: anything where the *string itself* is the signal.
2. **Dense retrieval (embeddings + cosine/ANN)** over free-text content — handles paraphrase, category-to-instance generalization, synonymy.
3. **Fusion**, then optionally a **reranking** pass over the fused top-N.

The reasoning is symmetric and well-documented: dense retrieval fails on exact strings (embeddings encode meaning, not characters — `RTX-4090` and `RTX-4070` sit almost on top of each other in embedding space despite being different products), while sparse retrieval fails on paraphrase (no shared tokens between "address proof" and "utility bill"). Neither subsumes the other; production systems run both and combine results, never one instead of the other.

**This is already this project's shape** — FR-11 (lexical, clients) and FR-12 (semantic, documents) split along the same line, just partitioned by entity type rather than run over every field. That partition is a reasonable simplification for this scale (clients are short structured records where lexical alone is defensible; document content is the one field where paraphrase actually occurs) and matches how the brief itself splits the two examples.

### 7.2 Reciprocal Rank Fusion is the standard answer to the score-normalization problem

**This bears directly on FR-13.** The standard way production hybrid systems combine a lexical score and a vector score is not to normalize both onto `[0,1]` and average — it's **Reciprocal Rank Fusion (RRF)**: each retriever ranks its own results independently, and the fused score is `Σ 1/(k + rank)` across retrievers (k is a small constant, typically 60). RRF operates on **rank position**, not raw score magnitude, which sidesteps the exact incompatibility problem FR-13 identifies (trigram similarity and cosine similarity are not on the same scale, and never will be). Reported gains from tuned hybrid + RRF over either method alone run around 5–10% NDCG on standard benchmarks.

> **Recommendation:** replace the "normalize both to `[0,1]` and blend" approach in FR-13 with RRF over two independently-ranked lists (client lexical results, document semantic results). It is less code, has no magic weighting constant to tune, and is the industry-default answer to exactly this problem — a stronger thing to defend in the design-decisions README section than a hand-tuned blend.

### 7.3 A cross-encoder reranking stage is common but not required here

Full production stacks add a reranking model over the fused top-N (e.g., top 50 → reranked to top 10) for a further relevance lift. At this assignment's scale (§1.2: ~10⁴ documents) this is a legitimate cut — it adds real latency and a second model to host for a corpus small enough that the base retrievers are unlikely to need the correction. Worth one sentence in the README as a "what we'd add at 10x the scale" note, not worth building.

### 7.4 The organizational-lookup case is a named, solved problem: entity resolution

§5.1's requirement — decompose `john.doe@neviswealth.com` to recover the organizational segment — is a specific instance of **entity/record linking**, a well-studied CRM and data-integration problem (matching records that refer to the same real-world entity — here, "the same organization" — despite no shared exact field). The lightweight version that fits this assignment's scope is exactly what FR-11 already proposes: tokenize identifiers on their structural delimiters (`@ . - /`) and index the fragments, rather than building a general entity-resolution pipeline (which would be over-engineering for ~10³ clients). Confirms FR-11's approach rather than changing it.

## 7. What this changes in the requirements

Concrete deltas against [requirements.md](requirements.md):

| # | Change | Rationale |
|---|---|---|
| 1 | **Promote the compliance argument for local embeddings** to the lead design decision (NFR-28) | Document content never crossing the trust boundary is a stronger, more domain-native justification than reviewer convenience — and mirrors Nevis's own published stance |
| 2 | **Add `firm_id`/tenant column to the data model** even though multi-tenancy stays out of scope | The real product is multi-tenant B2B; a schema that structurally precludes isolation is the wrong answer even in a demo. Cheap now, expensive later |
| 3 | **Extend FR-11 lexical matching to `social_links`** | Same organizational-lookup workflow as the email domain; explains why the field is in the brief's schema |
| 4 | **Make the seed corpus a realistic KYC/onboarding document set** (NFR-5) | Demonstrates the actual §5.2 workflow rather than a toy synonym pair |
| 5 | **Add a small relevance evaluation set** — ~10 query→expected-result pairs run as a test | Turns "semantic search works" from an assertion into evidence; directly addresses the "correctness" axis the brief says it is grading |
| 6 | **Reframe search logging (NFR-24) as audit-relevant**, and require queries be logged without PII leakage | Rule 204-2 makes retrieval activity a records concern; client names and emails in plaintext logs are a liability |
| 7 | **State the append-only growth assumption** in §1.2 scale assumptions | Five-year mandated retention means the corpus only grows; it frames why index strategy matters beyond demo scale |
| 8 | **Replace FR-13's `[0,1]`-normalize-and-blend merge with Reciprocal Rank Fusion** | §6.2: RRF is the industry-standard answer to exactly this score-incompatibility problem, needs no tuned weighting constant, and is a stronger thing to defend than a hand-normalized blend |
| 9 | **Add one sentence to the README noting cross-encoder reranking as a deliberate cut at this scale**, not an oversight | §6.3: standard in full production stacks; correctly out of scope at ~10⁴ documents, but worth naming so it reads as a scale-aware decision |
| 10 | **Name the embedding-locality divergence from Nevis's likely production approach explicitly** in the README design-decisions section | §1: their OpenAI/Anthropic enterprise agreements suggest hosted-API inference in production; our local-model choice is driven by NFR-6/NFR-28, not a claim that hosted APIs are wrong — better to say so than let a reviewer wonder if we missed it |

None of these change the locked stack decisions. Items 1, 3, 4, 5 and 8 are low-cost and materially improve how the submission reads; item 2 is a single column; items 6, 7, 9 and 10 are documentation.

---

## Sources

- [Nevis — official site](https://www.neviswealth.com)
- [Nevis raises $40M to accelerate AI innovation in wealth management](https://www.neviswealth.com/news/nevis-raised-40m/)
- [Nevis bags $35m to build AI for wealth management — FinTech Global](https://fintech.global/2025/12/02/nevis-bags-35m-to-build-ai-for-wealth-management/)
- [WealthTech Startup Nevis Secures $35M — Connect Money](https://www.connectmoney.com/stories/wealthtech-startup-nevis-secures-35m-to-expand-ai-powered-advisor-platform/)
- [Nevis Wealth Technology Ltd — Companies House](https://find-and-update.company-information.service.gov.uk/company/16016151)
- [Partnering with Nevis: Building the AI Operating System for Wealth Management — ICONIQ](https://www.iconiq.com/growth/insights/partnering-with-nevis-building-the-ai-operating-system-for-wealth-management)
- [Hybrid Search: BM25, Vector & Reranking Reference 2026 — Digital Applied](https://www.digitalapplied.com/blog/hybrid-search-bm25-vector-reranking-reference-2026)
- [Hybrid Search in Production: Why BM25 Still Wins on the Queries That Matter — TianPan.co](https://tianpan.co/blog/2026-04-12-hybrid-search-production-bm25-dense-embeddings)
- [Hybrid Search Guide: Vectors & Full-Text (April 2026) — Supermemory](https://supermemory.ai/blog/hybrid-search-guide/)
- [Nevis Wealth Technology — Tracxn company profile](https://tracxn.com/d/companies/nevis-wealth-technology/__p3ifDFjuzoI9qBdMiXdr48-IsMHivm-4uXzwZ7OlK3I)
- [AI Notetakers & Agentic OS for Financial Advisors: 2026 Buyer's Guide — WealthTech Today](https://wealthtechtoday.com/ai-notetakers-financial-advisors-2026/)
- [The End of Best-of-Breed? Why Unified Wealth Platforms Are Gaining Ground — WealthTech Today](https://wealthtechtoday.com/2026/05/29/ep-344-the-end-of-best-of-breed-why-unified-wealth-platforms-are-gaining-ground-with-rich-cancro-advisorengine/)
- [Jump, Zeplyn, and Zocks Now Integrated with AdvisorEngine — WealthManagement.com](https://www.wealthmanagement.com/artificial-intelligence/three-popular-ai-notetakers-now-integrated-with-advisorengine)
- [AI Trends Transforming Wealth Management Platforms — WealthManagement.com](https://www.wealthmanagement.com/artificial-intelligence/ai-trends-reshaping-wealth-management-in-2026)
- [How to Build an RIA Technology Stack in 2026 — Milemarker](https://lp.milemarker.co/ria-tech-stack-guide)
- [Integration strategies for multi-custodian RIA firms — CloudQix](https://cloudqix.com/resources/blog/multi-custodian-ria-integration-strategy/)
- [Investment Advisers Act of 1940 Rule 204-2 — Smarsh](https://www.smarsh.com/regulations/investment-advisers-act-of-1940-rule-204-2/)
- [Books and Records Requirements for RIAs — COMPLY](https://www.comply.com/resource/books-and-records-requirements-for-rias/)
- [Inside the US RIA Toolkit: structure and scale of the RIA market — The Wealth Mosaic](https://www.thewealthmosaic.com/vendors/the-wealth-mosaic/blogs/inside-the-us-ria-toolkit/)
- [4 growth trends affecting advisors and RIAs in 2026 — Capital Group](https://www.capitalgroup.com/ria/insights/articles/four-growth-trends-affecting-advisors-RIAs-2026.html)
- [50+ Key RIA Industry Statistics — CircleBlack](https://www.circleblack.com/key-ria-industry-statistics/)
