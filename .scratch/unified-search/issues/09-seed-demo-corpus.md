# 09 — Seed the demo corpus on startup

**What to build:** A reviewer's first `docker compose up` yields a system with data already in it,
so search returns meaningful results before they have created anything.

The seeder loads the same corpus the eval set uses and writes it through the ordinary creation path,
so seeded documents are chunked and embedded exactly as live ones are. Seeding through SQL would
produce rows whose embeddings came from nowhere — or worse, from a different model than the one
running. It runs only when the database is empty, and is safe to run twice.

**Blocked by:** 05 — Create and fetch documents.

**Status:** ready-for-agent

- [x] On a clean volume, startup seeds the corpus and search returns meaningful results with no manual setup
- [x] Seeding goes through the same creation path the API uses, not raw SQL
- [x] Nothing is seeded when clients already exist
- [x] Seeding can be turned off by configuration
- [x] Two instances starting at once cannot double-seed
- [x] The seed corpus and the eval corpus are the same file, so the demo and the tests cannot drift apart
