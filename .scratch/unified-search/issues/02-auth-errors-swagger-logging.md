# 02 — API-key auth, RFC 9457 errors, Swagger UI, request logging

**What to build:** Every API route requires a valid API key header; anything else is rejected with a
problem-detail response. Health and the OpenAPI documents stay open, so a reviewer can explore the
surface before authenticating. Swagger UI becomes the interactive surface the design commits to
instead of a frontend, with an authorize control that sends the header on every try-it-out call —
a reviewer forced to hand-craft headers is a poor first impression.

Errors never leak stack traces, SQL or constraint names, in this ticket or any later one; the handler
built here is what guarantees it. Every request carries a correlation id through structured logs and
back out on the response, which is the foundation the audit line in ticket 12 builds on.

**Blocked by:** 01 — Walking skeleton.

**Status:** ready-for-agent

- [ ] A missing, wrong or malformed key returns 401 as `application/problem+json`, and the comparison is constant-time
- [ ] Startup fails with a clear message when the configured key is unset or shorter than 32 characters
- [ ] The health endpoint and the OpenAPI and Swagger paths are reachable without a key; every other API route is not
- [ ] Swagger UI shows an authorize control for the key header and sends it on try-it-out calls
- [ ] Uncaught exceptions render as problem-detail responses with no stack trace, SQL or constraint name in the body
- [ ] Logs are structured JSON, each line carrying a request id that is also returned on the response
