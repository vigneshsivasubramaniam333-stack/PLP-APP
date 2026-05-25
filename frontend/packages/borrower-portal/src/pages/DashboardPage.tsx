import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { loanApi, useAuth } from '@plp/shared';
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
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800">Welcome back, {user?.fullName || 'Borrower'}</h1>
        <p className="text-sm text-slate-500 mt-1">Manage your loans and repayments</p>
      </div>

      {!borrowerId ? (
        <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-8">
          Your profile is not linked as a borrower. Contact support if this is unexpected.
        </p>
      ) : loading ? (
        <div className="flex justify-center py-16 mb-8">
          <div className="animate-pulse text-slate-400 text-sm">Loading your summary…</div>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
          <StatCard label="Loans" value={String(stats.loanCount)} sub="All records" accent="slate" />
          <StatCard label="Total outstanding" value={formatCurrency(stats.totalOutstanding)} sub="DISBURSED + due" accent="amber" />
          <StatCard label="Amount due" value={formatCurrency(stats.amountDue)} sub="REPAYMENT_DUE" accent="orange" />
          <StatCard label="Overdue" value={formatCurrency(stats.overdueAmount)} sub="OVERDUE" accent="red" />
          <StatCard label="Pending requests" value={String(stats.pendingRequests)} sub="REQUESTED" accent="sky" />
        </div>
      )}

      {/* Quick Actions */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <Link
          to="/request-loan"
          className="group bg-white rounded-xl border border-slate-200 p-5 hover:border-sky-300 hover:shadow-md"
        >
          <div className="w-10 h-10 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center mb-3 group-hover:bg-sky-100">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-slate-800">Request Pay Day Loan</h3>
          <p className="text-xs text-slate-500 mt-1">Check eligibility and apply for a salary advance</p>
        </Link>

        <Link
          to="/invoice-discounting"
          className="group bg-white rounded-xl border border-slate-200 p-5 hover:border-purple-300 hover:shadow-md"
        >
          <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center mb-3 group-hover:bg-purple-100">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-slate-800">Invoice Discounting</h3>
          <p className="text-xs text-slate-500 mt-1">Discount eligible invoices for immediate funds</p>
        </Link>

        <Link
          to="/my-loans"
          className="group bg-white rounded-xl border border-slate-200 p-5 hover:border-emerald-300 hover:shadow-md"
        >
          <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3 group-hover:bg-emerald-100">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
              />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-slate-800">View My Loans</h3>
          <p className="text-xs text-slate-500 mt-1">Track status, outstanding amounts, and repay</p>
        </Link>
      </div>

      {/* How it works */}
      <div className="bg-white rounded-xl border border-slate-200 p-6">
        <h3 className="text-sm font-semibold text-slate-700 mb-4">How Pay Day Loans Work</h3>
        <div className="grid grid-cols-1 sm:grid-cols-5 gap-4">
          {[
            { step: '1', title: 'Salary Upload', desc: 'Your employer uploads salary data for the pay period' },
            { step: '2', title: 'Eligibility', desc: 'Eligible amount is calculated based on accumulated salary' },
            { step: '3', title: 'Apply', desc: 'Request a loan up to your eligible limit' },
            { step: '4', title: 'Approval', desc: 'Loan is reviewed and approved automatically or manually' },
            { step: '5', title: 'Repayment', desc: 'Amount is deducted from your next salary' },
          ].map((s) => (
            <div key={s.step} className="text-center">
              <div className="w-8 h-8 rounded-full bg-sky-50 text-sky-600 font-bold text-sm mx-auto mb-2 flex items-center justify-center">
                {s.step}
              </div>
              <h4 className="text-xs font-semibold text-slate-700">{s.title}</h4>
              <p className="text-[11px] text-slate-500 mt-1">{s.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  sub,
  accent,
}: {
  label: string;
  value: string;
  sub: string;
  accent: 'slate' | 'amber' | 'orange' | 'red' | 'sky';
}) {
  const ring: Record<typeof accent, string> = {
    slate: 'bg-slate-50 text-slate-700 ring-slate-200',
    amber: 'bg-amber-50 text-amber-800 ring-amber-200',
    orange: 'bg-orange-50 text-orange-800 ring-orange-200',
    red: 'bg-red-50 text-red-800 ring-red-200',
    sky: 'bg-sky-50 text-sky-800 ring-sky-200',
  };
  return (
    <div className={`rounded-xl border shadow-sm p-5 ring-1 ${ring[accent]}`}>
      <div className="text-xs font-medium uppercase tracking-wide opacity-80">{label}</div>
      <div className="text-xl font-bold mt-1 tabular-nums">{value}</div>
      <div className="text-[11px] opacity-70 mt-1">{sub}</div>
    </div>
  );
}
