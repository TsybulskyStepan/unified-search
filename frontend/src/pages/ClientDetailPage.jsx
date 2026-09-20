import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getClient, getClientDocuments, createDocument } from '../api/client';
import ClientInfo from '../components/ClientInfo';
import DocumentList from '../components/DocumentList';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function ClientDetailPage() {
  const { clientId } = useParams();
  const [client, setClient] = useState(null);
  const [documents, setDocuments] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [clientData, docsData] = await Promise.all([
        getClient(clientId),
        getClientDocuments(clientId),
      ]);
      setClient(clientData);
      setDocuments(docsData);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [clientId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;

    setCreating(true);
    setCreateError(null);
    try {
      await createDocument(clientId, { title: title.trim(), content: content.trim() });
      setTitle('');
      setContent('');
      setShowForm(false);
      // Refresh the document list
      const docsData = await getClientDocuments(clientId);
      setDocuments(docsData);
    } catch (err) {
      setCreateError(err);
    } finally {
      setCreating(false);
    }
  };

  if (loading) return <LoadingSpinner text="Loading client…" />;
  if (error) return <ErrorMessage error={error} onRetry={fetchData} />;

  return (
    <div className="client-detail-page">
      <ClientInfo client={client} />

      <div className="document-section">
        <div className="document-section-header">
          <h3>Documents ({documents?.length || 0})</h3>
          <button className="btn btn-primary btn-sm" onClick={() => setShowForm(!showForm)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {showForm ? 'Cancel' : 'Document'}
          </button>
        </div>

        {showForm && (
          <form className="add-document-form" onSubmit={handleCreate}>
            <label>
              Title
              <input
                type="text"
                className="form-input"
                placeholder="Document title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                autoFocus
              />
            </label>
            <label>
              Content
              <textarea
                className="form-input form-textarea"
                placeholder="Document content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                required
                rows={5}
              />
            </label>
            {createError && <p className="error-text">{createError.message}</p>}
            <div className="add-document-actions">
              <button type="submit" className="btn btn-primary" disabled={creating}>
                {creating ? 'Creating…' : 'Create Document'}
              </button>
            </div>
          </form>
        )}

        <DocumentList documents={documents} clientId={clientId} />
      </div>
    </div>
  );
}
