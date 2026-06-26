import { Link, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { useAuth, PortalSidebarBrand, PortalPoweredByFooter, paymentCartApi } from '@plp/shared';
import { useBorrowerFlowEnrollments } from '../hooks/useBorrowerFlowEnrollments';

function navLinkClass(isActive: boolean) {
  return `${isActive ? 'bt-sidebar-link active' : 'bt-sidebar-link'} flex items-center gap-3`;
}

export default function BorrowerLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const borrowerId = useMemo(() => {
    if ((user?.linkedEntityType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
    return (user?.linkedEntityId ?? '').trim();
  }, [user?.linkedEntityType, user?.linkedEntityId]);
  const flowFlags = useBorrowerFlowEnrollments(borrowerId);
  const [cartCount, setCartCount] = useState(0);

  const lendingItems = useMemo(() => {
    const items: { path: string; label: string; icon: typeof InvoiceIcon; badgeKey?: 'cart' }[] = [
      { path: '/request-loan', label: 'Request Loan', icon: LoanIcon },
    ];
    if (flowFlags.purchaseBill) {
      items.push({ path: '/invoice-discounting', label: 'Invoice Discounting', icon: InvoiceIcon });
    }
    if (flowFlags.salesBill) {
      items.push({ path: '/sales-bill-discounting', label: 'Sales Bill Discounting', icon: InvoiceIcon });
    }
    if (flowFlags.purchaseOrder) {
      items.push({ path: '/purchase-order-discounting', label: 'Purchase Order Discounting', icon: InvoiceIcon });
    }
    if (flowFlags.purchaseBill || flowFlags.salesBill || flowFlags.purchaseOrder) {
      items.push({ path: '/payments/cart', label: 'Payment cart', icon: CartIcon, badgeKey: 'cart' });
    }
    items.push({ path: '/my-loans', label: 'My Loans', icon: ListIcon });
    return items;
  }, [flowFlags]);

  const navGroups = useMemo(
    () => [
      {
        label: 'Overview',
        items: [
          { path: '/', label: 'Dashboard', icon: HomeIcon },
          { path: '/programs', label: 'Programs', icon: ProgramsIcon },
        ],
      },
      { label: 'Lending', items: lendingItems },
      {
        label: 'Account',
        items: [
          { path: '/repayments', label: 'Repayments', icon: RepaymentIcon },
          { path: '/notifications', label: 'Notifications', icon: BellIcon },
        ],
      },
    ],
    [lendingItems],
  );

  useEffect(() => {
    if (!borrowerId) return;
    const refresh = () => {
      paymentCartApi.count(borrowerId).then((res) => setCartCount(res.data?.data ?? 0)).catch(() => setCartCount(0));
    };
    refresh();
    window.addEventListener('plp-payment-cart-changed', refresh);
    return () => window.removeEventListener('plp-payment-cart-changed', refresh);
  }, [borrowerId]);

  return (
    <div className="min-h-screen bt-app-canvas flex flex-col">
      <div className="flex flex-1 min-h-0">
        <aside className="bt-sidebar-wide w-64 shrink-0 flex flex-col">
          <PortalSidebarBrand portalTitle="Borrower Portal" metaLine={user?.email ?? undefined} />

          <nav className="bt-sidebar-wide-nav flex-1">
            {navGroups.map((group) => (
              <div key={group.label} className="mb-1">
                <div className="bt-sidebar-group-label">{group.label}</div>
                {group.items.map((item) => {
                  const isActive = location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
                  return (
                    <Link key={item.path} to={item.path} className={navLinkClass(isActive)}>
                      <item.icon active={isActive} />
                      <span className="flex-1">{item.label}</span>
                      {item.badgeKey === 'cart' && cartCount > 0 ? (
                        <span className="ml-auto inline-flex min-w-[1.25rem] items-center justify-center rounded-full bg-[var(--bt-orange)] px-1.5 py-0.5 text-[10px] font-bold text-white">
                          {cartCount}
                        </span>
                      ) : null}
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>

          <div className="border-t border-[var(--bt-gray-200)] p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--bt-orange-light)] text-xs font-semibold text-[var(--bt-orange)]">
                {user?.fullName?.charAt(0) || 'B'}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{user?.fullName}</div>
                <div className="text-[11px] text-[var(--bt-gray-500)]">Borrower</div>
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

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
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
function LoanIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function InvoiceIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
function CartIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25h9.75m-9.75 0L5.106 5.272M7.5 14.25 5.106 5.272m0 0H3.375m2.731 0h13.11c.552 0 1.02.402 1.106.94l1.149 6.598a1.125 1.125 0 01-1.106 1.315H6.622m0 0a2.25 2.25 0 100 4.5 2.25 2.25 0 000-4.5zm9 0a2.25 2.25 0 100 4.5 2.25 2.25 0 000-4.5z" />
    </svg>
  );
}
function ListIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
    </svg>
  );
}
function RepaymentIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  );
}
function BellIcon({ active }: { active: boolean }) {
  return (
    <svg className={iconClass(active)} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
    </svg>
  );
}
