const BASE = '/api';

function getApiKey() {
  return localStorage.getItem('nevis_api_key') || '';
}

async function request(path, options = {}) {
  const apiKey = getApiKey();
  const headers = {
    'Content-Type': 'application/json',
    ...(apiKey ? { 'X-API-Key': apiKey } : {}),
    ...options.headers,
  };

  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    throw new ApiError('Unauthorized. Please set a valid API key.', 401);
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const detail = body?.detail || body?.title || `Request failed (${res.status})`;
    throw new ApiError(detail, res.status);
  }

  return res;
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// ── Search ──────────────────────────────────────────────────────────

export async function search(query, { limit, offset } = {}) {
  const params = new URLSearchParams();
  if (query) params.set('q', query);
  if (limit != null) params.set('limit', String(limit));
  if (offset != null) params.set('offset', String(offset));

  const res = await request(`/search?${params}`);
  const total = parseInt(res.headers.get('X-Total-Count') || '0', 10);
  const results = await res.json();
  return { results, total };
}

// ── Clients ─────────────────────────────────────────────────────────

export async function getClients() {
  const res = await request('/clients');
  return res.json();
}

export async function getClient(clientId) {
  const res = await request(`/clients/${clientId}`);
  return res.json();
}

export async function createClient(client) {
  const res = await request('/clients', {
    method: 'POST',
    body: JSON.stringify(client),
  });
  return res.json();
}

// ── Documents ───────────────────────────────────────────────────────

export async function getClientDocuments(clientId) {
  const res = await request(`/clients/${clientId}/documents`);
  return res.json();
}

export async function createDocument(clientId, document) {
  const res = await request(`/clients/${clientId}/documents`, {
    method: 'POST',
    body: JSON.stringify(document),
  });
  return res.json();
}

export async function getDocument(clientId, documentId) {
  const res = await request(`/clients/${clientId}/documents/${documentId}`);
  return res.json();
}

export async function requestSummary(clientId, documentId) {
  const res = await request(`/clients/${clientId}/documents/${documentId}/summary`, {
    method: 'POST',
  });
  return res.json();
}
