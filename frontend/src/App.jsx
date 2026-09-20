import React, { useEffect, useState } from 'react';
import { Outlet, Link, useNavigate } from 'react-router-dom';
import { search } from './api/client';
import ApiKeyModal from './components/ApiKeyModal';
import { useApiKey } from './hooks/useApiKey';

export default function App() {
  const { apiKey, setApiKey } = useApiKey();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [previewOpen, setPreviewOpen] = useState(false);

  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([]);
      return undefined;
    }

    let current = true;
    const timeout = setTimeout(async () => {
      try {
        const { results } = await search(query, { limit: 5 });
        if (current) {
          setSuggestions(results.slice(0, 5));
        }
      } catch {
        if (current) {
          setSuggestions([]);
        }
      }
    }, 250);

    return () => {
      current = false;
      clearTimeout(timeout);
    };
  }, [apiKey, query]);

  const handleSubmit = (e) => {
    e.preventDefault();
    const form = e.target;
    const query = form.elements.q.value.trim();
    if (query) {
      setPreviewOpen(false);
      navigate(`/?q=${encodeURIComponent(query)}`);
    }
  };

  return (
    <div className="app">
      <header className="header">
        <div className="header-inner">
          <Link to="/" className="logo">
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.35-4.35" />
            </svg>
            Search System
          </Link>

          <form className="header-search" onSubmit={handleSubmit}>
            <input
              name="q"
              type="search"
              placeholder="Search clients and documents…"
              className="search-input header-search-input"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPreviewOpen(true);
              }}
              onFocus={() => setPreviewOpen(true)}
              onBlur={() => setTimeout(() => setPreviewOpen(false), 150)}
            />
            {previewOpen && query.trim() && (
              <ul className="search-preview" aria-label="Search suggestions">
                {suggestions.map((result, index) => (
                  <li
                    key={`${result.type}-${result.client?.id || result.document?.id}-${index}`}
                    className={`search-preview-item search-preview-item--${result.type}`}
                  >
                    <Link
                      to={result.type === 'client'
                        ? `/clients/${result.client.id}`
                        : `/clients/${result.document.client_id}/documents/${result.document.id}`}
                      onClick={() => setPreviewOpen(false)}
                    >
                      <span className="search-preview-title">
                        {result.type === 'client'
                          ? `${result.client.first_name} ${result.client.last_name}`
                          : result.document.title}
                      </span>
                      <span className="search-preview-subtitle">
                        {result.type === 'client'
                          ? result.client.email
                          : result.document.client_name}
                      </span>
                      {result.type === 'document' && (
                        <span className="search-preview-tags">
                          {result.document.document_type && (
                            <span className="tag tag-type">{result.document.document_type}</span>
                          )}
                          {result.document.purposes?.slice(0, 2).map((p) => (
                            <span key={p} className="tag tag-purpose">{p}</span>
                          ))}
                        </span>
                      )}
                    </Link>
                  </li>
                ))}
                {suggestions.length === 0 && (
                  <li className="search-preview-empty">No matches</li>
                )}
              </ul>
            )}
          </form>

          <button
            className="api-key-btn"
            onClick={() =>
              document.getElementById('api-key-modal').classList.add('visible')
            }
            title={apiKey ? 'API key configured' : 'Set API key'}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </button>
          <a className="swagger-link" href="/swagger-ui/index.html">
            Swagger
          </a>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <ApiKeyModal apiKey={apiKey} setApiKey={setApiKey} />
    </div>
  );
}
