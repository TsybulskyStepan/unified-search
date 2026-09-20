import React from 'react';
import { Link } from 'react-router-dom';

export default function DocumentList({ documents, clientId }) {
  if (!documents || documents.length === 0) {
    return (
      <div className="empty-state">
        <p>No documents for this client.</p>
      </div>
    );
  }

  return (
    <div className="document-list">
      <h3>Documents ({documents.length})</h3>
      {documents.map((doc) => (
        <Link
          key={doc.id}
          to={`/clients/${clientId}/documents/${doc.id}`}
          className="document-card"
        >
          <div className="document-card-body">
            <h4>{doc.title}</h4>
            {doc.summary && doc.summary_status === 'ready' && (
              <p className="summary-preview">{doc.summary}</p>
            )}
            <div className="result-tags">
              {doc.document_type && (
                <span className="tag tag-type">{doc.document_type}</span>
              )}
              {doc.purposes?.map((p) => (
                <span key={p} className="tag tag-purpose">
                  {p}
                </span>
              ))}
            </div>
          </div>
          <div className="document-card-meta">
            <span className="doc-date">
              {new Date(doc.created_at).toLocaleDateString()}
            </span>
            <span className="doc-arrow">&rarr;</span>
          </div>
        </Link>
      ))}
    </div>
  );
}
