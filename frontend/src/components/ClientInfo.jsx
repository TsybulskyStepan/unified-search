import React from 'react';
import { Link } from 'react-router-dom';

export default function ClientInfo({ client }) {
  if (!client) return null;

  return (
    <div className="client-info">
      <div className="client-info-header">
        <h2>
          {client.first_name} {client.last_name}
        </h2>
        <Link to="/" className="btn btn-secondary btn-sm">
          &larr; Back to search
        </Link>
      </div>

      <dl className="client-details">
        <div className="detail-row">
          <dt>Email</dt>
          <dd>{client.email}</dd>
        </div>
        {client.description && (
          <div className="detail-row">
            <dt>Description</dt>
            <dd>{client.description}</dd>
          </div>
        )}
        {client.social_links?.length > 0 && (
          <div className="detail-row">
            <dt>Social Links</dt>
            <dd>
              <ul className="social-links">
                {client.social_links.map((link, i) => (
                  <li key={i}>
                    <a href={link} target="_blank" rel="noopener noreferrer">
                      {link}
                    </a>
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        )}
        <div className="detail-row">
          <dt>Created</dt>
          <dd>{new Date(client.created_at).toLocaleDateString()}</dd>
        </div>
      </dl>
    </div>
  );
}
