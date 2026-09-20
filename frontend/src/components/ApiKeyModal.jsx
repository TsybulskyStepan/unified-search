import React, { useState } from 'react';

export default function ApiKeyModal({ apiKey, setApiKey }) {
  const [value, setValue] = useState(apiKey);

  const handleSave = (e) => {
    e.preventDefault();
    setApiKey(value.trim());
    document.getElementById('api-key-modal').classList.remove('visible');
  };

  const handleClear = () => {
    setApiKey('');
    setValue('');
    document.getElementById('api-key-modal').classList.remove('visible');
  };

  return (
    <div id="api-key-modal" className="modal-overlay">
      <div className="modal">
        <h2 className="modal-title">API Key</h2>
        <p className="modal-desc">
          The API requires an <code>X-API-Key</code> header. Enter the key from
          your <code>.env</code> or docker-compose configuration.
        </p>
        <form onSubmit={handleSave}>
          <input
            type="text"
            className="modal-input"
            placeholder="Paste your API key…"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            autoFocus
          />
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={handleClear}>
              Clear
            </button>
            <button type="submit" className="btn btn-primary">
              Save
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
