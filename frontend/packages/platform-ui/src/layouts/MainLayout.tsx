import { useMemo, useRef, useState, useEffect } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth, PortalSidebarBrand, PortalPoweredByFooter } from '@plp/shared';
import { canViewWorkbench } from '../pages/MakerCheckerPage';

function buildNavGroups(role: string | undefined) {
  return [
    {
      label: 'Overview',
      items: [
        { path: '/', label: 'Dashboard', icon: DashboardIcon },
        { path: '/users', label: 'Users', icon: UsersIcon },
      ],
    },
    {
      label: 'Lending',
      items: [
        { path: '/programs', label: 'Programs', icon: ProgramsIcon },
        { path: '/sub-programs', label: 'Sub Programs', icon: SubProgramsIcon },
        { path: '/anchors', label: 'Anchors', icon: AnchorsIcon },
        { path: '/borrowers', label: 'Borrowers', icon: BorrowersIcon },
        { path: '/loans', label: 'Loans', icon: LoansIcon },
      ],
    },
    {
      label: 'Operations',
      items: [
        { path: '/reports', label: 'Reports', icon: ReportsIcon },
        ...(canViewWorkbench(role)
          ? [{ path: '/workbench', label: 'Workbench', icon: WorkbenchIcon }]
          : []),
        { path: '/audit', label: 'Audit Trail', icon: AuditIcon },
        { path: '/notifications', label: 'Notifications', icon: NotificationsIcon },
      ],
    },
  ];
}

function getPageTitle(pathname: string, groups: ReturnType<typeof buildNavGroups>): string {
  for (const g of groups) {
    for (const item of g.items) {
      if (pathname === item.path) {
        return item.label;
      }
    }
  }
  return 'Page';
}

export default function MainLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navGroups = useMemo(() => buildNavGroups(user?.role), [user?.role]);
  const pageTitle = useMemo(
    () => getPageTitle(location.pathname, navGroups),
    [location.pathname, navGroups],
  );

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col">
      <div className="flex flex-1 min-h-0">
        <aside
          className="w-64 flex flex-col shrink-0 text-white"
          style={{ backgroundColor: 'var(--brand-navy)' }}
        >
          <div className="border-b border-white/10">
            <PortalSidebarBrand portalTitle="Platform Admin" />
          </div>

          <nav className="flex-1 py-3 overflow-y-auto">
            {navGroups.map((group) => (
              <div key={group.label} className="mb-1">
                <div className="px-5 py-2 text-[10px] font-semibold uppercase tracking-wider text-white/40">
                  {group.label}
                </div>
                {group.items.map((item) => {
                  const isActive = location.pathname === item.path;
                  return (
                    <Link
                      key={item.path}
                      to={item.path}
                      className={`flex items-center gap-3 mx-2 px-3 py-2 rounded-r-lg text-[13px] font-medium border-l-[3px] transition-colors ${
                        isActive
                          ? 'border-[#2563EB] bg-[rgba(255,255,255,0.07)] text-white'
                          : 'border-transparent text-[rgba(255,255,255,0.55)] hover:bg-white/[0.05] hover:text-white/90'
                      }`}
                    >
                      <item.icon active={isActive} />
                      {item.label}
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>
        </aside>

        <main className="flex-1 flex flex-col min-w-0 overflow-hidden bg-[var(--surface)]">
          <header className="h-16 shrink-0 flex items-center justify-between gap-4 px-6 border-b border-slate-200/90 bg-white">
            <h2 className="text-lg font-semibold text-slate-900 font-sans tracking-tight">{pageTitle}</h2>
            <HeaderUserMenu user={user} onSignOut={logout} />
          </header>
          <div className="flex-1 overflow-auto min-h-0">
            <div className="max-w-7xl mx-auto px-6 py-6">
              <Outlet />
            </div>
          </div>
        </main>
      </div>
      <PortalPoweredByFooter className="shrink-0" />
    </div>
  );
}

function HeaderUserMenu({
  user,
  onSignOut,
}: {
  user: { fullName?: string | null; role?: string; email?: string | null } | null;
  onSignOut: () => void;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const initial = user?.fullName?.charAt(0) || user?.email?.charAt(0) || 'U';

  return (
    <div className="relative shrink-0" ref={wrapRef}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-200 text-sm font-semibold text-slate-700 ring-2 ring-white ring-offset-2 ring-offset-white hover:bg-slate-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand-accent)]"
        aria-expanded={open}
        aria-haspopup="true"
        aria-label="Account menu"
      >
        {initial}
      </button>
      {open ? (
        <div
          className="absolute right-0 top-12 z-50 w-56 rounded-lg border border-slate-200 bg-white py-2 shadow-lg"
          role="menu"
        >
          <div className="border-b border-slate-100 px-3 py-2">
            <p className="truncate text-sm font-semibold text-slate-900 font-sans">{user?.fullName || 'User'}</p>
            <p className="truncate text-xs text-slate-500 font-sans">
              {user?.role ? user.role.split('_').join(' ') : ''}
            </p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onSignOut();
            }}
            className="w-full px-3 py-2 text-left text-sm font-medium text-red-600 hover:bg-red-50 font-sans"
          >
            Sign out
          </button>
        </div>
      ) : null}
    </div>
  );
}

function DashboardIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  );
}

function UsersIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
    </svg>
  );
}

function ProgramsIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
    </svg>
  );
}

function SubProgramsIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
    </svg>
  );
}

function AnchorsIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  );
}

function BorrowersIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}

function LoansIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function ReportsIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
    </svg>
  );
}

function WorkbenchIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}

function AuditIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
    </svg>
  );
}

function NotificationsIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`w-4 h-4 shrink-0 ${active ? 'text-white' : 'text-[rgba(255,255,255,0.55)]'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
    </svg>
  );
}
