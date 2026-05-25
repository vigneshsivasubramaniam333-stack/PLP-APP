import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '@plp/shared';
import App from './App';
import './index.css';

window.__PLP_TOKEN_KEY__ = 'plp_platform_token';
window.__PLP_REFRESH_KEY__ = 'plp_platform_refresh';
window.__PLP_USER_KEY__ = 'plp_platform_user';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter basename="/plp">
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);