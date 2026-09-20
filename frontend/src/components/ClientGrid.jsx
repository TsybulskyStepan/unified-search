import React from 'react';
import { Link } from 'react-router-dom';

export default function ClientGrid({ clients }) {
  return (
    <section aria-labelledby="clients-heading">
      <div className="section-heading">
        <div>
          <h1 id="clients-heading">Clients</h1>
          <p>Choose a client or add a new one.</p>
        </div>
      </div>

      <div className="client-grid">
        <Link to="/clients/new" className="client-tile client-tile--add">
          <span className="add-client-icon" aria-hidden="true">+</span>
          <span>Add client</span>
        </Link>
        {clients?.map((client) => (
          <Link key={client.id} to={`/clients/${client.id}`} className="client-tile">
            <h2>{client.first_name} {client.last_name}</h2>
            <p>{client.email}</p>
          </Link>
        ))}
      </div>
    </section>
  );
}
