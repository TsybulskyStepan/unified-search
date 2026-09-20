import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getClients, search } from '../api/client';
import ClientGrid from '../components/ClientGrid';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SearchResults from '../components/SearchResults';

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q') || '';
  const [results, setResults] = useState(null);
  const [total, setTotal] = useState(0);
  const [clients, setClients] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const performSearch = useCallback(async (value) => {
    setLoading(true);
    setError(null);
    try {
      const data = await search(value, { limit: 50 });
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

  const loadClients = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setClients(await getClients());
    } catch (err) {
      setError(err);
      setClients(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (query.trim()) {
      performSearch(query);
    } else {
      setResults(null);
      setTotal(0);
      loadClients();
    }
  }, [loadClients, performSearch, query]);

  const retry = query.trim() ? () => performSearch(query) : loadClients;

  return (
    <div className="search-page">
      {loading && <LoadingSpinner text="Loading…" />}
      {error && <ErrorMessage error={error} onRetry={retry} />}
      {!loading && !error && query.trim() && (
        <SearchResults results={results} total={total} query={query} />
      )}
      {!loading && !error && !query.trim() && <ClientGrid clients={clients} />}
    </div>
  );
}
