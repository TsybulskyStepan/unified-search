import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getDocument, getClient, requestSummary } from '../api/client';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function DocumentDetailPage() {
  const { clientId, documentId } = useParams();
  const [document, setDocument] = useState(null);
  const [client, setClient] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [docData, clientData] = await Promise.all([
        getDocument(clientId, documentId),
        getClient(clientId),
      ]);
      setDocument(docData);
      setClient(clientData);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [clientId, documentId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRequestSummary = async () => {
    setSummaryLoading(true);
    setSummaryError(null);
    try {
      const updated = await requestSummary(clientId, documentId);
      setDocument(updated);
      // If still pending, poll
      if (updated.summary_status === 'pending') {
        pollSummary();
      }
    } catch (err) {
      setSummaryError(err);
      setSummaryLoading(false);
    }
  };

  const pollSummary = async () => {
    const poll = async () => {
      try {
        const updated = await getDocument(clientId, documentId);
        setDocument(updated);
        if (updated.summary_status === 'pending') {
          setTimeout(poll, 2000);
        } else {
          setSummaryLoading(false);
        }
      } catch {
        setSummaryLoading(false);
      }
    };
    setTimeout(poll, 2000);
  };

  if (loading) return <LoadingSpinner text="Loading document…" />;
  if (error) return <ErrorMessage error={error} onRetry={fetchData} />;

  const clientName = client
    ? `${client.first_name} ${client.last_name}`
    : '';

  return (
    <div className="document-detail-page">
      <div className="breadcrumbs">
        <Link to="/">Search</Link>
        <span className="sep">/</span>
        <Link to={`/clients/${clientId}`}>{clientName}</Link>
        <span className="sep">/</span>
        <span>{document.title}</span>
      </div>

      <div className="doc-header">
        <h2>{document.title}</h2>
        <div className="result-tags">
          {document.document_type && (
            <span className="tag tag-type">{document.document_type}</span>
          )}
          {document.purposes?.map((p) => (
            <span key={p} className="tag tag-purpose">
              {p}
            </span>
          ))}
        </div>
      </div>

      {/* Summary section */}
      <div className="doc-summary-section">
        <h3>Summary</h3>
        {document.summary_status === 'ready' && document.summary ? (
          <div className="doc-summary">{document.summary}</div>
        ) : document.summary_status === 'pending' ? (
          <div className="doc-summary-pending">
            <LoadingSpinner text="Generating summary…" />
          </div>
        ) : (
          <div className="doc-summary-empty">
            <p>No summary available.</p>
            <button
              className="btn btn-primary btn-sm"
              onClick={handleRequestSummary}
              disabled={summaryLoading}
            >
              {summaryLoading ? 'Requesting…' : 'Generate Summary'}
            </button>
            {summaryError && (
              <p className="error-text">{summaryError.message}</p>
            )}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="doc-content-section">
        <h3>Content</h3>
        <div className="doc-content">{document.content}</div>
      </div>
    </div>
  );
}
