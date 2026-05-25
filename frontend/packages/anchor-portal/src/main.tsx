import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '@plp/shared';
import './index.css';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import EmployeesPage from './pages/EmployeesPage';
import SalaryUploadPage from './pages/SalaryUploadPage';
import InvoiceUploadPage from './pages/InvoiceUploadPage';
import SettlementsPage from './pages/SettlementsPage';
import ReportsPage from './pages/ReportsPage';
import AnchorLayout from './layouts/AnchorLayout';

window.__PLP_TOKEN_KEY__ = 'plp_anchor_token';
window.__PLP_REFRESH_KEY__ = 'plp_anchor_refresh';
window.__PLP_USER_KEY__ = 'plp_anchor_user';

const ANCHOR_PORTAL_ROLES = new Set(['ANCHOR_ADMIN', 'ANCHOR_MAKER', 'ANCHOR_CHECKER']);

function isAnchorPortalRole(role: string | undefined): boolean {
  return role != null && ANCHOR_PORTAL_ROLES.has(role);
}

function UnauthorizedPortalAccess() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const handleSignOut = () => {
    logout();
    navigate('/login', { replace: true });
  };
  return (
    <div className="min-h-screen bg-slate-100 flex flex-col items-center justify-center p-6">
      <div className="max-w-md w-full bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
        <h1 className="text-lg font-semibold text-slate-800">Unauthorized portal access</h1>
        <p className="text-sm text-slate-600 mt-2">You do not have access to this portal.</p>
        <button
          type="button"
          onClick={handleSignOut}
          className="mt-6 w-full px-4 py-2.5 text-sm font-semibold text-white bg-slate-900 rounded-lg hover:bg-slate-800"
        >
          Sign out
        </button>
      </div>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!isAnchorPortalRole(user.role)) return <UnauthorizedPortalAccess />;
  return <>{children}</>;
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter basename="/plp-anchor">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<ProtectedRoute><AnchorLayout /></ProtectedRoute>}>
            <Route index element={<DashboardPage />} />
            <Route path="employees" element={<EmployeesPage />} />
            <Route path="salary-upload" element={<SalaryUploadPage />} />
            <Route path="invoice-upload" element={<InvoiceUploadPage />} />
            <Route path="settlements" element={<SettlementsPage />} />
            <Route path="reports" element={<ReportsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);