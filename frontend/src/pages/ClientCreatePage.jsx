import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createClient, createDocument } from '../api/client';
import ErrorMessage from '../components/ErrorMessage';

function emptyDocument() {
  return { key: crypto.randomUUID(), title: '', content: '' };
}

export default function ClientCreatePage() {
  const navigate = useNavigate();
  const [client, setClient] = useState({ first_name: '', last_name: '', email: '' });
  const [documents, setDocuments] = useState([emptyDocument()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [createdClient, setCreatedClient] = useState(null);

  const updateClient = (event) => {
    setClient((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const updateDocument = (key, field, value) => {
    setDocuments((current) => current.map((document) => (
      document.key === key ? { ...document, [field]: value } : document
    )));
  };

  const removeDocument = (key) => {
    setDocuments((current) => current.filter((document) => document.key !== key));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);

    const populatedDocuments = documents.filter((document) => document.title || document.content);
    if (populatedDocuments.some((document) => !document.title.trim() || !document.content.trim())) {
      setError(new Error('Each document needs both a title and content.'));
      return;
    }

    setSubmitting(true);
    try {
      const savedClient = await createClient(client);
      setCreatedClient(savedClient);
      for (const document of populatedDocuments) {
        await createDocument(savedClient.id, {
          title: document.title,
          content: document.content,
        });
      }
      navigate(`/clients/${savedClient.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="client-create-page">
      <div className="page-heading">
        <div>
          <h1>Add client</h1>
          <p>Create a client and optionally attach document text.</p>
        </div>
        <Link to="/" className="btn btn-secondary">Cancel</Link>
      </div>

      {error && <ErrorMessage error={error} />}
      {createdClient && error && (
        <p className="created-client-notice">
          The client was created. <Link to={`/clients/${createdClient.id}`}>Open client</Link>
        </p>
      )}

      <form className="client-form" onSubmit={handleSubmit}>
        <fieldset>
          <legend>Client details</legend>
          <div className="form-grid">
            <label>
              First name
              <input name="first_name" value={client.first_name} onChange={updateClient} maxLength="100" required />
            </label>
            <label>
              Last name
              <input name="last_name" value={client.last_name} onChange={updateClient} maxLength="100" required />
            </label>
            <label className="form-field--wide">
              Email
              <input name="email" type="email" value={client.email} onChange={updateClient} maxLength="254" required />
            </label>
          </div>
        </fieldset>

        <fieldset>
          <div className="fieldset-heading">
            <div>
              <legend>Documents</legend>
              <p>Document text is indexed as soon as it is saved.</p>
            </div>
            <button type="button" className="btn btn-secondary" onClick={() => setDocuments((current) => [...current, emptyDocument()])}>
              + Add document
            </button>
          </div>

          {documents.map((document, index) => (
            <div className="document-input" key={document.key}>
              <div className="document-input-heading">
                <h2>Document {index + 1}</h2>
                <button type="button" className="text-button" onClick={() => removeDocument(document.key)}>
                  Remove
                </button>
              </div>
              <label>
                Title
                <input value={document.title} onChange={(event) => updateDocument(document.key, 'title', event.target.value)} maxLength="300" />
              </label>
              <label>
                Content
                <textarea value={document.content} onChange={(event) => updateDocument(document.key, 'content', event.target.value)} maxLength="64000" rows="7" />
              </label>
            </div>
          ))}
        </fieldset>

        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : 'Save client'}
        </button>
      </form>
    </div>
  );
}
