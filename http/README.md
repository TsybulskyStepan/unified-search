# Runnable request files

Six `.http` files that exercise every endpoint against a locally running stack.

```bash
docker compose up -d          # from the repository root
curl -s localhost:8080/health # wait for {"status":"UP"}
```

Open any file and click **Send Request** above a request.

- **VS Code / Cursor** — install the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension. Requests that need an id from an earlier response chain automatically through `{{name.response.body.$.id}}`, so send the requests in a file top to bottom.
- **IntelliJ / JetBrains** — the HTTP Client runs these too, but it does not read the `{{name.response.body.$.id}}` chaining syntax. Send the request that creates the record, copy the `id` from the response, and set it at the top of the file.

Both tools read `@key = value` at the top of a file, so the base URL and API key are defined once there. The key is the dev-only default from `docker-compose.yaml`; if you set your own `API_KEY` in `.env`, change `@apiKey` to match.

| File | What it covers |
|---|---|
| `01-quickstart.http` | The two cases from the assignment brief, plus health |
| `02-clients.http` | Create and fetch a client |
| `03-documents.http` | Create and fetch a document, searchable immediately |
| `04-search.http` | Every query shape: identity, category, compound, no-match, paging |
| `05-summaries.http` | Request a summary and watch the state machine |
| `06-errors.http` | Auth, validation, conflict and not-found responses |

Seeded ids are random on every fresh volume, so nothing here hardcodes one; the files look records up by search or create them first.
