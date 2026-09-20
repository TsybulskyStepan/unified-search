import React from 'react';
import { Link } from 'react-router-dom';

export default function SearchResults({ results, total, query }) {
  if (!results || results.length === 0) {
    if (query) {
      return (
        <div className="empty-state">
          <p>No results found for <strong>{query}</strong></p>
        </div>
      );
    }
    return (
      <div className="empty-state">
        <p>Enter a query above to search across clients and documents.</p>
      </div>
    );
  }

  return (
    <div className="search-results">
      <p className="results-count">
        {total} result{total !== 1 ? 's' : ''} for <strong>{query}</strong>
      </p>

      {results.map((result, i) => (
        <ResultCard key={`${result.type}-${result.client?.id || result.document?.id}-${i}`} result={result} />
      ))}
    </div>
  );
}

function ResultCard({ result }) {
  const isClient = result.type === 'client';

  if (isClient) {
    const c = result.client;
    return (
      <Link to={`/clients/${c.id}`} className="result-card result-card--client">
        <div className="result-type-badge client-badge">Client</div>
        <div className="result-body">
          <h3 className="result-title">
            {c.first_name} {c.last_name}
          </h3>
          <p className="result-subtitle">{c.email}</p>
          {c.description && <p className="result-desc">{c.description}</p>}
          {result.match && (
            <p className="result-match">
              Matched on <strong>{result.match.field}</strong> ({result.match.tier})
            </p>
          )}
        </div>
        <div className="result-score">{result.score}</div>
      </Link>
    );
  }

  // Document result
  const d = result.document;
  const clientName = d.client_name || '';
  return (
    <Link
      to={`/clients/${d.client_id}/documents/${d.id}`}
      className="result-card result-card--document"
    >
      <div className="result-type-badge document-badge">Document</div>
      <div className="result-body">
        <h3 className="result-title">{d.title}</h3>
        {clientName && <p className="result-subtitle">Client: {clientName}</p>}
        {result.match?.passage && (
          <p className="result-passage">{result.match.passage}</p>
        )}
        {d.summary && d.summary_status === 'ready' && (
          <p className="result-summary">{d.summary}</p>
        )}
        <div className="result-tags">
          {d.document_type && (
            <span className="tag tag-type">{d.document_type}</span>
          )}
          {d.purposes?.map((p) => (
            <span key={p} className="tag tag-purpose">
              {p}
            </span>
          ))}
        </div>
      </div>
      <div className="result-score">{result.score}</div>
    </Link>
  );
}
