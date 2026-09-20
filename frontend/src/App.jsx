import React from 'react';
import { Outlet, Link, useNavigate } from 'react-router-dom';
import ApiKeyModal from './components/ApiKeyModal';
import { useApiKey } from './hooks/useApiKey';

export default function App() {
  const { apiKey, setApiKey } = useApiKey();
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    const form = e.target;
    const query = form.elements.q.value.trim();
    if (query) {
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
              defaultValue={
                new URLSearchParams(window.location.search).get('q') || ''
              }
            />
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
