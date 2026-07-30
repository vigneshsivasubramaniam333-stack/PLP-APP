import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { apiClient, useAuth, PortalSidebarBrand, PortalPoweredByFooter } from '@plp/shared';
import { useAnchorFlowSubPrograms } from '../hooks/useAnchorFlowSubPrograms';
import { useAnchorEarlyPayEnabled } from '../hooks/useAnchorEarlyPayEnabled';
import { anchorIdFromUser } from '../invoice/invoiceShared';

function isNavActive(pathname: string, itemPath: string): boolean {
  if (itemPath === '/') return pathname === '/';
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`);
}

function navLinkClass(isActive: boolean) {
  return `${isActive ? 'bt-sidebar-link active' : 'bt-sidebar-link'} flex items-center gap-3`;
}

type OnboardingSummary = {
  onboardingStatus?: string | null;
  forceOnboarding?: boolean;
  menusUnlocked?: boolean;
  showMyApplication?: boolean;
  canResume?: boolean;
};

export default function AnchorLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );
  const flowFlags = useAnchorFlowSubPrograms(anchorId);
  const earlyPayEnabled = useAnchorEarlyPayEnabled();
  const [onboarding, setOnboarding] = useState<OnboardingSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { data: body } = await apiClient.get<{ data?: OnboardingSummary } | OnboardingSummary>(
          '/api/v1/portal/anchor/onboarding',
        );
        const summary =
          body && typeof body === 'object' && 'data' in body && (body as { data?: OnboardingSummary }).data
            ? (body as { data: OnboardingSummary }).data
            : (body as OnboardingSummary);
        if (!cancelled) setOnboarding(summary);
      } catch {
        // Fail closed: keep menus locked until onboarding API confirms COMPLETED.
        if (!cancelled) {
          setOnboarding({ menusUnlocked: false, forceOnboarding: true, showMyApplication: true });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  // While status is unknown, show a minimal onboarding-only nav (not the full portal).
  const unlocked = onboarding == null ? false : Boolean(onboarding.menusUnlocked);
  const forceOnboarding = onboarding != null && Boolean(onboarding.forceOnboarding);
  // Always offer My application during onboarding so anchors can track status after submit / send-back.
  const showMyApplication = onboarding == null ? true : onboarding.showMyApplication !== false;

  const invoiceDiscountingItems = useMemo(() => {
    const items: { path: string; label: string; icon: typeof DocIcon }[] = [];
    if (flowFlags.purchaseBill) {
      items.push({ path: '/invoices', label: 'Invoices', icon: DocIcon });
    }
    if (flowFlags.salesBill) {
      items.push({ path: '/sales-bill-discounting', label: 'Sales Bill Discounting', icon: DocIcon });
    }
    if (flowFlags.purchaseOrder) {
      items.push({ path: '/purchase-order-discounting', label: 'Purchase Order Discounting', icon: DocIcon });
    }
    if (flowFlags.salesBill && earlyPayEnabled) {
      items.push({ path: '/early-pay', label: 'Early Pay', icon: DocIcon });
    }
    return items;
  }, [flowFlags, earlyPayEnabled]);

  const navGroups = useMemo(() => {
    if (!unlocked) {
      const items: { path: string; label: string; icon: typeof ChartIcon }[] = [
        { path: '/onboarding', label: 'Onboarding', icon: ProgramsIcon },
      ];
      if (showMyApplication) {
        items.push({ path: '/my-application', label: 'My application', icon: DocIcon });
      }
      return [{ label: 'Onboarding', items }];
    }
    const groups = [
      {
        label: 'Overview',
        items: [
          { path: '/', label: 'Dashboard', icon: ChartIcon },
          { path: '/programs', label: 'Programs', icon: ProgramsIcon },
          ...(showMyApplication
            ? [{ path: '/my-application', label: 'My application', icon: DocIcon }]
            : []),
        ],
      },
    ];
    if (flowFlags.paydayLoan) {
      groups.push({
        label: 'Pay Day Loan',
        items: [
          { path: '/employees', label: 'Employees', icon: UsersIcon },
          { path: '/salary-upload', label: 'Salary Upload', icon: UploadIcon },
        ],
      });
    }
    if (invoiceDiscountingItems.length > 0) {
      groups.push({ label: 'Invoice Discounting', items: invoiceDiscountingItems });
    }
    groups.push({
      label: 'Operations',
      items: [
        { path: '/settlements', label: 'Settlements', icon: CardIcon },
        { path: '/reports', label: 'Reports', icon: ReportIcon },
      ],
    });
    return groups;
  }, [flowFlags.paydayLoan, invoiceDiscountingItems, unlocked, showMyApplication]);

  const path = location.pathname;
  const onOnboardingRoute = path === '/onboarding' || path.startsWith('/onboarding/') || path === '/my-application';
  // Until summary loads, keep full menus hidden and stay on onboarding routes.
  const awaitingSummary = onboarding == null;
  if ((awaitingSummary || forceOnboarding) && !onOnboardingRoute) {
    return <Navigate to="/onboarding" replace />;
  }

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
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-[var(--bt-gray-800)] truncate">{user?.fullName}</p>
                <p className="text-xs text-[var(--bt-gray-500)] truncate">{user?.role}</p>
              </div>
              <button type="button" onClick={logout} className="text-xs text-[var(--bt-gray-500)] hover:text-[var(--bt-orange)]">
                Logout
              </button>
            </div>
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

function ChartIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <path d="M4 19V5M4 19h16M8 17V10M12 17V7M16 17v-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
function ProgramsIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M3 9h18" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}
function UsersIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <circle cx="9" cy="8" r="3" stroke="currentColor" strokeWidth="1.8" />
      <path d="M3 19c0-2.5 2.5-4.5 6-4.5s6 2 6 4.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="17" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}
function UploadIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <path d="M12 16V4M12 4l-4 4M12 4l4 4M4 20h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
function DocIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <path d="M7 3h7l5 5v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" stroke="currentColor" strokeWidth="1.8" />
      <path d="M14 3v5h5M9 13h6M9 17h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
function CardIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M3 10h18" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}
function ReportIcon({ active }: { active?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" className={active ? 'text-[var(--bt-orange)]' : 'text-[var(--bt-gray-500)]'}>
      <path d="M6 4h9l3 3v13H6V4z" stroke="currentColor" strokeWidth="1.8" />
      <path d="M9 12h6M9 16h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
