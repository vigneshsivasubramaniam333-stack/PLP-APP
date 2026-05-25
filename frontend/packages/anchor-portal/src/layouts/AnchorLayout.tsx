import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth, PortalSidebarBrand, PortalPoweredByFooter } from '@plp/shared';

const navGroups = [
  {
    label: 'Overview',
    items: [
      { path: '/', label: 'Dashboard', icon: ChartIcon },
    ],
  },
  {
    label: 'Pay Day Loan',
    items: [
      { path: '/employees', label: 'Employees', icon: UsersIcon },
      { path: '/salary-upload', label: 'Salary Upload', icon: UploadIcon },
    ],
  },
  {
    label: 'Invoice Discounting',
    items: [
      { path: '/invoice-upload', label: 'Invoice Upload', icon: DocIcon },
    ],
  },
  {
    label: 'Operations',
    items: [
      { path: '/settlements', label: 'Settlements', icon: CardIcon },
      { path: '/reports', label: 'Reports', icon: ReportIcon },
    ],
  },
];

export default function AnchorLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col">
      <div className="flex flex-1 min-h-0">
      <aside className="w-64 bg-slate-900 text-white flex flex-col shrink-0">
        <div className="border-b border-slate-700/50">
          <PortalSidebarBrand portalTitle="Anchor Portal" metaLine={user?.email} />
        </div>

        <nav className="flex-1 py-3 overflow-y-auto">
          {navGroups.map((group) => (
            <div key={group.label} className="mb-1">
              <div className="px-5 py-2 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                {group.label}
              </div>
              {group.items.map((item) => {
                const isActive = location.pathname === item.path;
                return (
                  <Link key={item.path} to={item.path}
                    className={`flex items-center gap-3 mx-2 px-3 py-2 rounded-lg text-[13px] font-medium ${
                      isActive
                        ? 'bg-emerald-600/20 text-emerald-400'
                        : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                    }`}>
                    <item.icon active={isActive} />
                    {item.label}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-700/50">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center text-xs font-semibold text-slate-300">
              {user?.fullName?.charAt(0) || 'A'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-slate-200 truncate">{user?.fullName}</div>
              <div className="text-[11px] text-slate-500">Anchor</div>
            </div>
          </div>
          <button onClick={logout}
            className="mt-3 w-full text-left text-xs text-slate-500 hover:text-red-400">
            Sign out
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto min-w-0">
        <div className="max-w-6xl mx-auto px-6 py-6">
          <Outlet />
        </div>
      </main>
      </div>
      <PortalPoweredByFooter className="shrink-0" />
    </div>
  );
}

function ChartIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
    </svg>
  );
}
function UsersIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}
function UploadIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
    </svg>
  );
}
function DocIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
function CardIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  );
}
function ReportIcon({ active }: { active: boolean }) {
  return (
    <svg className={`w-4 h-4 ${active ? 'text-emerald-400' : 'text-slate-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
