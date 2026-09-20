import assert from 'node:assert/strict';
import test from 'node:test';

const storage = new Map();
globalThis.localStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: (key) => storage.delete(key),
};

const { createClient, createDocument, getClients, search } = await import('../src/api/client.js');

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

test('loads the client tiles with the stored API key', async () => {
  storage.set('nevis_api_key', 'test-key');
  let received;
  globalThis.fetch = async (url, options) => {
    received = { url, options };
    return jsonResponse([]);
  };

  await getClients();

  assert.equal(received.url, '/api/clients');
  assert.equal(received.options.headers['X-API-Key'], 'test-key');
});

test('limits a search preview to five results', async () => {
  let received;
  globalThis.fetch = async (url, options) => {
    received = { url, options };
    return jsonResponse([]);
  };

  await search('address proof', { limit: 5 });

  assert.equal(received.url, '/api/search?q=address+proof&limit=5');
});

test('creates a client, then attaches a text document to it', async () => {
  const requests = [];
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return jsonResponse({ id: 'client-id' }, 201);
  };

  await createClient({
    first_name: 'Ada',
    last_name: 'Lovelace',
    email: 'ada@example.com',
  });
  await createDocument('client-id', {
    title: 'Identity document',
    content: 'Passport number redacted',
  });

  assert.deepEqual(requests.map((request) => request.url), [
    '/api/clients',
    '/api/clients/client-id/documents',
  ]);
  assert.deepEqual(JSON.parse(requests[0].options.body), {
    first_name: 'Ada',
    last_name: 'Lovelace',
    email: 'ada@example.com',
  });
  assert.deepEqual(JSON.parse(requests[1].options.body), {
    title: 'Identity document',
    content: 'Passport number redacted',
  });
});
