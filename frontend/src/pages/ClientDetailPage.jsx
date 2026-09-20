import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getClient, getClientDocuments } from '../api/client';
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

  if (loading) return <LoadingSpinner text="Loading client…" />;
  if (error) return <ErrorMessage error={error} onRetry={fetchData} />;

  return (
    <div className="client-detail-page">
      <ClientInfo client={client} />
      <DocumentList documents={documents} clientId={clientId} />
    </div>
  );
}
