import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth, PortalSidebarBrand, PortalPoweredByFooter } from '@plp/shared';

const navGroups = [
  {
    label: 'Overview',
    items: [
      { path: '/', label: 'Dashboard', icon: ChartIcon },
      { path: '/programs', label: 'Programs', icon: ProgramsIcon },
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
      { path: '/invoices', label: 'Invoices', icon: DocIcon },
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

function isNavActive(pathname: string, itemPath: string): boolean {
  if (itemPath === '/') return pathname === '/';
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`);
}

function navLinkClass(isActive: boolean) {
  return `${isActive ? 'bt-sidebar-link active' : 'bt-sidebar-link'} flex items-center gap-3`;
}

export default function AnchorLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();

  return (
    <div className="min-h-screen bt-app-canvas flex flex-col">
      <div className="flex flex-1 min-h-0">
        <aside className="bt-sidebar-wide w-64 shrink-0 flex flex-col">
          <PortalSidebarBrand portalTitle="Anchor Portal" metaLine={user?.email} />

          <nav className="bt-sidebar-wide-nav flex-1">
            {navGroups.map((group) => (
              <div key={group.label} className="mb-1">
                <div className="bt-sidebar-group-label">{group.label}</div>
                {group.items.map((item) => {
                  const isActive = isNavActive(location.pathname, item.path);
                  return (
                    <Link key={item.path} to={item.path} className={navLinkClass(isActive)}>
                      <item.icon active={isActive} />
                      {item.label}
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>

          <div className="border-t border-[var(--bt-gray-200)] p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--bt-orange-light)] text-xs font-semibold text-[var(--bt-orange)]">
                {user?.fullName?.charAt(0) || 'A'}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{user?.fullName}</div>
                <div className="text-[11px] text-[var(--bt-gray-500)]">Anchor</div>
              </div>
            </div>
            <button type="button" onClick={logout} className="mt-3 text-xs text-[var(--bt-red)] hover:underline">
              Sign out
            </button>
          </div>
        </aside>

        <main className="bt-main-shell">
          <div className="bt-main-content">
            <Outlet />
          </div>
        </main>
      </div>
      <PortalPoweredByFooter className="shrink-0" />
    </div>
  );
}

function iconClass(active: boolean) {
  return `h-4 w-4 shrink-0 ${active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-400)]'}`;
}

function ChartIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
    </svg>
  );
}
function ProgramsIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
    </svg>
  );
}
function UsersIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}
function UploadIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
    </svg>
  );
}
function DocIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
function CardIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  );
}
function ReportIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
