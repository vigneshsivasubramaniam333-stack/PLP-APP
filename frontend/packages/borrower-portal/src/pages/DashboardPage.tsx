import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  loanApi,
  useAuth,
  CreditLimitDashboardSection,
  useBorrowerCreditLimits,
  BtPageHeader,
  BtCard,
  BtCardHeader,
  BtStatCard,
} from '@plp/shared';
import type { Loan } from '@plp/shared';

const REFRESH_EVENT = 'plp-borrower-loans-changed';

function borrowerIdFromAuth(
  linkedType: string | null | undefined,
  linkedId: string | null | undefined,
): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
  return (linkedId ?? '').trim();
}

function computeStats(loans: Loan[]) {
  const loanCount = loans.length;
  let totalOutstanding = 0;
  let amountDue = 0;
  let overdueAmount = 0;
  for (const l of loans) {
    const o = Number(l.outstandingAmount) || 0;
    if (l.status === 'OVERDUE') {
      overdueAmount += o;
      totalOutstanding += o;
    } else if (l.status === 'REPAYMENT_DUE') {
      amountDue += o;
      totalOutstanding += o;
    } else if (l.status === 'DISBURSED') {
      totalOutstanding += o;
    }
  }
  const pendingRequests = loans.filter((l) => l.status === 'REQUESTED').length;
  return { loanCount, totalOutstanding, amountDue, overdueAmount, pendingRequests };
}

export default function DashboardPage() {
  const { user } = useAuth();
  const borrowerId = useMemo(
    () => borrowerIdFromAuth(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);
  const { rows: limitRows, loading: limitsLoading } = useBorrowerCreditLimits(borrowerId);

  const loadStats = useCallback(async () => {
    if (!borrowerId) {
      setLoans([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await loanApi.list({ borrowerId });
      setLoans((res.data?.data as Loan[] | undefined) ?? []);
    } catch {
      setLoans([]);
    } finally {
      setLoading(false);
    }
  }, [borrowerId]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useEffect(() => {
    const onRefresh = () => void loadStats();
    window.addEventListener(REFRESH_EVENT, onRefresh);
    return () => window.removeEventListener(REFRESH_EVENT, onRefresh);
  }, [loadStats]);

  const stats = useMemo(() => computeStats(loans), [loans]);

  const formatCurrency = (n: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);

  return (
    <div>
      <BtPageHeader
        title={`Welcome back, ${user?.fullName || 'Borrower'}`}
        description="Manage your loans and repayments"
      />

      {!borrowerId ? (
        <p className="bt-alert bt-alert-warning text-sm mb-8">
          Your profile is not linked as a borrower. Contact support if this is unexpected.
        </p>
      ) : null}

      {borrowerId ? (
        <CreditLimitDashboardSection
          rows={limitRows}
          loading={limitsLoading}
          title="Your credit limits"
          subtitle="Limit, utilized, and available headroom across your programs"
        />
      ) : null}

      {borrowerId && !loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
          <BtStatCard title="Loans" value={String(stats.loanCount)} subtitle="All records" accent="gray" />
          <BtStatCard title="Total outstanding" value={formatCurrency(stats.totalOutstanding)} subtitle="DISBURSED + due" accent="amber" />
          <BtStatCard title="Amount due" value={formatCurrency(stats.amountDue)} subtitle="REPAYMENT_DUE" accent="orange" />
          <BtStatCard title="Overdue" value={formatCurrency(stats.overdueAmount)} subtitle="OVERDUE" accent="red" />
          <BtStatCard title="Pending requests" value={String(stats.pendingRequests)} subtitle="REQUESTED" accent="blue" />
        </div>
      ) : borrowerId && loading ? (
        <div className="flex justify-center py-8 mb-8">
          <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading loan summary…</div>
        </div>
      ) : null}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <Link
          to="/request-loan"
          className="group bt-card p-5 hover:border-[var(--bt-orange)] hover:shadow-md transition-colors"
        >
          <div className="w-10 h-10 rounded-lg bg-[var(--bt-orange-light)] text-[var(--bt-orange)] flex items-center justify-center mb-3 group-hover:opacity-90">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-[var(--bt-gray-800)]">Request Pay Day Loan</h3>
          <p className="text-xs text-[var(--bt-gray-500)] mt-1">Check eligibility and apply for a salary advance</p>
        </Link>

        <Link
          to="/invoice-discounting"
          className="group bt-card p-5 hover:border-[var(--bt-orange)] hover:shadow-md transition-colors"
        >
          <div className="w-10 h-10 rounded-lg bg-[var(--bt-blue-bg)] text-[var(--bt-blue)] flex items-center justify-center mb-3 group-hover:opacity-90">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-[var(--bt-gray-800)]">Invoice Discounting</h3>
          <p className="text-xs text-[var(--bt-gray-500)] mt-1">Discount eligible invoices for immediate funds</p>
        </Link>

        <Link
          to="/my-loans"
          className="group bt-card p-5 hover:border-[var(--bt-green)] hover:shadow-md transition-colors"
        >
          <div className="w-10 h-10 rounded-lg bg-[var(--bt-green-bg)] text-[var(--bt-green)] flex items-center justify-center mb-3 group-hover:opacity-90">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-[var(--bt-gray-800)]">View My Loans</h3>
          <p className="text-xs text-[var(--bt-gray-500)] mt-1">Track status, outstanding amounts, and repay</p>
        </Link>
      </div>

      <BtCard className="p-6">
        <BtCardHeader title="How Pay Day Loans Work" />
        <div className="grid grid-cols-1 sm:grid-cols-5 gap-4 mt-4">
          {[
            { step: '1', title: 'Salary Upload', desc: 'Your employer uploads salary data for the pay period' },
            { step: '2', title: 'Eligibility', desc: 'Eligible amount is calculated based on accumulated salary' },
            { step: '3', title: 'Apply', desc: 'Request a loan up to your eligible limit' },
            { step: '4', title: 'Approval', desc: 'Loan is reviewed and approved automatically or manually' },
            { step: '5', title: 'Repayment', desc: 'Amount is deducted from your next salary' },
          ].map((s) => (
            <div key={s.step} className="text-center">
              <div className="w-8 h-8 rounded-full bg-[var(--bt-orange-light)] text-[var(--bt-orange)] font-bold text-sm mx-auto mb-2 flex items-center justify-center">
                {s.step}
              </div>
              <h4 className="text-xs font-semibold text-[var(--bt-gray-700)]">{s.title}</h4>
              <p className="text-[11px] text-[var(--bt-gray-500)] mt-1">{s.desc}</p>
            </div>
          ))}
        </div>
      </BtCard>
    </div>
  );
}
