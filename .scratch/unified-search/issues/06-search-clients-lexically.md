# 06 — Search clients lexically, end to end

**What to build:** The first half of the product thesis — one query parameter returns ranked client
matches. Matching is per-field trigram word similarity across name, email, description and social
links, so a firm name that appears only inside an email domain still matches, and the field that
matched is reported back to the caller.

Matches below the lexical floor are dropped, so an unrelated query returns an empty list rather than
the least-bad client. Paging and the total are in place from the start, because they are cheap now
and awkward to retrofit once documents join the list.

This is where the brief's first example becomes an executable test, along with the misspelling case
that falls out of the same mechanism.

**Blocked by:** 04 — Create and fetch clients.

**Extended by:** 17 — client matches split into an identity tier and a context tier, so a description
hit no longer outranks the documents that answer the query (§6.2). The floor and the fields are
unchanged.

**Status:** complete

- [x] Searching for a firm name returns the client whose email carries that domain, first, reporting the email as the matched field
- [x] The same query also matches a client through a company URL in their social links
- [x] A misspelled surname returns the right client first
- [x] Matches below the lexical floor are excluded, and an unrelated query returns 200 with an empty array and a zero total — never 404
- [x] Limit and offset are validated per §4.2; pages are disjoint and cover the list; an offset beyond the total returns an empty array
- [x] The total count header reports the size of the above-floor candidate list
- [x] The query string reaches SQL only as a bound parameter
