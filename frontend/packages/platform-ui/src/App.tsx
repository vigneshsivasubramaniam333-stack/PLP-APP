import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '@plp/shared';
import MainLayout from './layouts/MainLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ProgramsPage from './pages/ProgramsPage';
import AnchorsPage from './pages/AnchorsPage';
import LoansPage from './pages/LoansPage';
import ReportsPage from './pages/ReportsPage';
import AuditTrailPage from './pages/AuditTrailPage';
import NotificationsPage from './pages/NotificationsPage';
import SubProgramsPage from './pages/SubProgramsPage';
import BorrowersPage from './pages/BorrowersPage';
import UsersPage from './pages/UsersPage';
import MakerCheckerPage, { canViewWorkbench } from './pages/MakerCheckerPage';

const LENDER_PORTAL_ROLES = new Set([
  'PLATFORM_ADMIN',
  'CREDIT_MANAGER',
  'CREDIT_ANALYST',
  'ACCOUNTS_OFFICER',
  'ACCOUNTS_MANAGER',
  'COMPLIANCE_OFFICER',
]);

function isLenderPortalRole(role: string | undefined): boolean {
  return role != null && LENDER_PORTAL_ROLES.has(role);
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
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isLenderPortalRole(user?.role)) return <UnauthorizedPortalAccess />;
  return <>{children}</>;
}

function WorkbenchRoute() {
  const { user } = useAuth();
  if (!canViewWorkbench(user?.role)) return <Navigate to="/" replace />;
  return <MakerCheckerPage />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="programs" element={<ProgramsPage />} />
        <Route path="sub-programs" element={<SubProgramsPage />} />
        <Route path="anchors" element={<AnchorsPage />} />
        <Route path="borrowers" element={<BorrowersPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="loans" element={<LoansPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="workbench" element={<WorkbenchRoute />} />
        <Route path="audit" element={<AuditTrailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
      </Route>
    </Routes>
  );
}
