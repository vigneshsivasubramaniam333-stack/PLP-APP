import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '@plp/shared';
import './index.css';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import LoanRequestPage from './pages/LoanRequestPage';
import MyLoansPage from './pages/MyLoansPage';
import InvoiceDiscountingPage from './pages/InvoiceDiscountingPage';
import RepaymentHistoryPage from './pages/RepaymentHistoryPage';
import NotificationsPage from './pages/NotificationsPage';
import BorrowerLayout from './layouts/BorrowerLayout';

window.__PLP_TOKEN_KEY__ = 'plp_borrower_token';
window.__PLP_REFRESH_KEY__ = 'plp_borrower_refresh';
window.__PLP_USER_KEY__ = 'plp_borrower_user';

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
  if (user.role !== 'BORROWER') return <UnauthorizedPortalAccess />;
  return <>{children}</>;
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter basename="/plp">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<ProtectedRoute><BorrowerLayout /></ProtectedRoute>}>
            <Route index element={<DashboardPage />} />
            <Route path="request-loan" element={<LoanRequestPage />} />
            <Route path="my-loans" element={<MyLoansPage />} />
            <Route path="invoice-discounting" element={<InvoiceDiscountingPage />} />
            <Route path="repayments" element={<RepaymentHistoryPage />} />
            <Route path="notifications" element={<NotificationsPage />} />
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