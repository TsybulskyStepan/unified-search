import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import App from './App';
import SearchPage from './pages/SearchPage';
import ClientDetailPage from './pages/ClientDetailPage';
import DocumentDetailPage from './pages/DocumentDetailPage';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<App />}>
          <Route path="/" element={<SearchPage />} />
          <Route path="/clients/:clientId" element={<ClientDetailPage />} />
          <Route
            path="/clients/:clientId/documents/:documentId"
            element={<DocumentDetailPage />}
          />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);
