# Unified Search

Search API over advisors' clients and documents: lexical matching for clients, semantic matching for documents, one ranked list. Take-home assignment; reviewers read the code *and* the history.

## Sources of truth

Read the relevant section before changing behaviour. Don't restate these docs here or in code comments.

| Question | Document |
|---|---|
| What is graded | [docs/assignment.md](docs/assignment.md) |
| What the product must do | [docs/prd.md](docs/prd.md) |
| How it is built: packages, schema, API contract, tests | [docs/system-design.md](docs/system-design.md) |

If code and docs disagree, stop and say so. Either the code is wrong, or the doc gets updated **in the same commit** with the reason.

## Commands

```bash
./gradlew check          # format check + compile + all tests (the definition of done)
./gradlew spotlessApply  # format everything (google-java-format)
./gradlew test --tests 'com.example.searchapp.search.*'   # one package
```

Java 25 via Gradle toolchain. Integration tests need Docker (Testcontainers, `pgvector/pgvector:pg17`).

## Invariants

Each one is enforced by a test once its code exists. Adding code that could break one means adding or extending that test.

- **Module boundary.** `onboarding` and `search` never import each other; both may use `shared`. They share two contracts, not one: the database schema, and the vector space — which is why `Embedder` lives in `shared`. Enforced by package structure only, not by a test; the ArchUnit test that once checked this was removed in `61c4854`.
- **Searchable on `201`.** A document row and all its chunk embeddings commit in one transaction.
- **No PII in logs.** Never log query text, names, emails, titles or content. Log IDs, lengths, counts and timings. Applies to the running service. The relevance eval may log its own fixture queries, which are synthetic, so its report is readable.
- **SQL is bound, never concatenated.** `JdbcClient` with parameters; no JPA.
- **One embedding model.** `Embedder` is the only class that imports LangChain4j. Every chunk records the model that produced it and search filters on it, so a model change can never silently mix vector spaces.
- **Migrations are additive** (expand/contract), Flyway only.
- **Errors are RFC 9457 ProblemDetail** and never expose stack traces, SQL or constraint names.
- **Search never depends on summaries.** Summary failure leaves documents searchable.

## How to work here

1. **Plan first** for anything beyond a one-file change: name the system-design sections it touches.
2. **Test first.** Write a failing test, make it pass, then refactor. Relevance changes (either floor, chunk size, ordering) must be backed by the eval set, with before/after numbers in the commit or PR.
3. **Verify for real.** Run `./gradlew check`, and for API changes run the app and exercise the endpoint. Report what you ran and what it showed.
4. **Keep scope tight.** No dependencies, endpoints, config knobs or abstractions that system-design doesn't call for. If one is needed, record it in the system-design section that owns it first.
5. **Commit small.** Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), one logical change each, with the `Co-Authored-By` trailer when AI wrote the code.

Hooks in `.claude/settings.json` format Java after each edit and block ending a turn when formatting or compilation fails.
