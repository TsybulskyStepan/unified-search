import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { search } from '../api/client';
import SearchResults from '../components/SearchResults';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

const DEBOUNCE_MS = 300;

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryFromUrl = searchParams.get('q') || '';

  const [query, setQuery] = useState(queryFromUrl);
  const [results, setResults] = useState(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const debounceRef = useRef(null);

  const performSearch = useCallback(async (q) => {
    if (!q.trim()) {
      setResults(null);
      setTotal(0);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const data = await search(q, { limit: 50 });
      setResults(data.results);
      setTotal(data.total);
    } catch (err) {
      setError(err);
      setResults(null);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  // Sync query from URL on mount
  useEffect(() => {
    if (queryFromUrl) {
      setQuery(queryFromUrl);
      performSearch(queryFromUrl);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleInputChange = (e) => {
    const value = e.target.value;
    setQuery(value);

    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(() => {
      setSearchParams(value ? { q: value } : {}, { replace: true });
      performSearch(value);
    }, DEBOUNCE_MS);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    setSearchParams(query ? { q: query } : {}, { replace: true });
    performSearch(query);
  };

  return (
    <div className="search-page">
      <form className="search-form" onSubmit={handleSubmit}>
        <div className="search-input-wrapper">
          <svg
            className="search-icon"
            width="20"
            height="20"
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
          <input
            type="search"
            className="search-input main-search-input"
            placeholder="Search by name, email, or document content…"
            value={query}
            onChange={handleInputChange}
            autoFocus
          />
        </div>
      </form>

      {loading && <LoadingSpinner text="Searching…" />}

      {error && <ErrorMessage error={error} onRetry={() => performSearch(query)} />}

      {!loading && !error && (
        <SearchResults results={results} total={total} query={query} />
      )}
    </div>
  );
}
