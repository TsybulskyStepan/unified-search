# 04 — Create and fetch clients

**What to build:** An advisor can create a client and read it back. Creation validates every field,
rejects a duplicate email that differs only in case, and returns the location of the new record. The
by-id read exists because it is what that location points at.

A client is searchable the moment creation succeeds, with nothing to index — lexical retrieval reads
the base columns directly. There is no service layer here: creation is validate, insert, and map the
uniqueness violation to a conflict, which the controller and repository cover between them.

**Blocked by:** 02 — API-key auth, RFC 9457 errors, Swagger UI, request logging.

**Status:** complete

- [x] Creating a client returns 201 with a location header and the created record, with no social links rendered as an empty list rather than null
- [x] Fetching by id returns the client; an unknown id and a malformed UUID both return 404
- [x] Validation failures return 400 listing the offending fields, and values that are blank after trimming are rejected
- [x] A social link that is not an absolute http or https URL is rejected at write time
- [x] A second client whose email differs only by case returns 409
- [x] A request body over the size cap is rejected before JSON binding
- [x] Any constraint violation other than the email uniqueness one surfaces as a server error rather than being disguised as a client error
