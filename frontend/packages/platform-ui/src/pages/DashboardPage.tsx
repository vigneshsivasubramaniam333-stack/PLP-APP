import { useEffect, useState } from 'react';
import { programApi, loanApi } from '@plp/shared';
import type { Program, Loan } from '@plp/shared';

export default function DashboardPage() {
  const [programs, setPrograms] = useState<Program[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchData() {
      try {
        const [progRes, loanRes] = await Promise.all([
          programApi.list(),
          loanApi.list(),
        ]);
        setPrograms(progRes.data.data || []);
        setLoans(loanRes.data.data || []);
      } catch (err) {
        console.error('Failed to fetch dashboard data:', err);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading dashboard...</div>
      </div>
    );
  }

  const activePrograms = programs.filter((p) => p.status === 'ACTIVE').length;
  const activeLoans = loans.filter((l) => ['DISBURSED', 'REPAYMENT_DUE'].includes(l.status)).length;
  const overdueLoans = loans.filter((l) => l.status === 'OVERDUE').length;
  const totalDisbursed = loans.filter(l => l.status !== 'REJECTED' && l.status !== 'REQUESTED')
    .reduce((sum, l) => sum + (l.requestedAmount || 0), 0);

  return (
    <div>
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1">Overview of your lending operations</p>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          title="Total Programs"
          value={programs.length.toString()}
          subtitle={`${activePrograms} active`}
          color="blue"
        />
        <StatCard
          title="Active Loans"
          value={activeLoans.toString()}
          subtitle={`of ${loans.length} total`}
          color="emerald"
        />
        <StatCard
          title="Overdue"
          value={overdueLoans.toString()}
          subtitle={overdueLoans > 0 ? 'Needs attention' : 'All clear'}
          color={overdueLoans > 0 ? 'red' : 'slate'}
        />
        <StatCard
          title="Disbursed Value"
          value={`${(totalDisbursed / 100000).toFixed(1)}L`}
          subtitle="Total amount"
          color="violet"
        />
      </div>

      {/* Tables Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Programs */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-200">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Programs</h3>
            <span className="text-xs text-slate-400">{programs.length} total</span>
          </div>
          {programs.length === 0 ? (
            <div className="p-8 text-center">
              <div className="text-slate-400 text-sm">No programs created yet</div>
              <p className="text-xs text-slate-400 mt-1">Programs will appear here once created</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-slate-500 uppercase tracking-wider">
                    <th className="px-5 py-3 font-semibold">Program</th>
                    <th className="px-5 py-3 font-semibold">Product</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {programs.slice(0, 5).map((p) => (
                    <tr key={p.id} className="hover:bg-slate-50/50">
                      <td className="px-5 py-3">
                        <div className="font-medium text-slate-800 text-sm">{p.programName}</div>
                        <div className="text-xs text-slate-400 font-mono">{p.programCode}</div>
                      </td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${
                          p.productType === 'PAY_DAY_LOAN'
                            ? 'bg-blue-50 text-blue-700'
                            : 'bg-purple-50 text-purple-700'
                        }`}>
                          {p.productType === 'PAY_DAY_LOAN' ? 'Pay Day Loan' : 'Invoice Discounting'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        <StatusBadge status={p.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Recent Loans */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-200">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Recent Loans</h3>
            <span className="text-xs text-slate-400">{loans.length} total</span>
          </div>
          {loans.length === 0 ? (
            <div className="p-8 text-center">
              <div className="text-slate-400 text-sm">No loans yet</div>
              <p className="text-xs text-slate-400 mt-1">Loans will appear here once requested</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-slate-500 uppercase tracking-wider">
                    <th className="px-5 py-3 font-semibold">Loan</th>
                    <th className="px-5 py-3 font-semibold text-right">Amount</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {loans.slice(0, 5).map((l) => (
                    <tr key={l.id} className="hover:bg-slate-50/50">
                      <td className="px-5 py-3">
                        <div className="font-mono text-xs text-slate-700">{l.loanNumber}</div>
                        <div className="text-xs text-slate-400">{l.productType === 'PAY_DAY_LOAN' ? 'PDL' : 'ID'}</div>
                      </td>
                      <td className="px-5 py-3 text-right font-medium text-slate-800">
                        {formatCurrency(l.requestedAmount)}
                      </td>
                      <td className="px-5 py-3">
                        <LoanStatusBadge status={l.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatCurrency(amount: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
}

function StatCard({
  title,
  value,
  subtitle,
  color,
}: {
  title: string;
  value: string;
  subtitle: string;
  color: string;
}) {
  const colors: Record<string, { border: string; value: string }> = {
    blue: { border: 'border-t-blue-600', value: 'text-blue-600' },
    emerald: { border: 'border-t-emerald-600', value: 'text-emerald-600' },
    red: { border: 'border-t-red-600', value: 'text-red-600' },
    slate: { border: 'border-t-slate-500', value: 'text-slate-600' },
    violet: { border: 'border-t-violet-600', value: 'text-violet-600' },
  };
  const c = colors[color] || colors.blue;
  return (
    <div
      className={`rounded-xl border border-slate-200 border-t-4 ${c.border} bg-white shadow-sm px-5 pt-5 pb-5`}
    >
      <p className="font-mono text-[10px] font-medium uppercase tracking-wider text-slate-500">{title}</p>
      <p className={`mt-2 font-serif text-[28px] font-normal leading-none ${c.value}`}>{value}</p>
      <p className="mt-2 text-xs text-slate-500">{subtitle}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    ACTIVE: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    DRAFT: 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20',
    PAUSED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    CLOSED: 'bg-red-50 text-red-700 ring-1 ring-red-600/20',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${styles[status] || styles.DRAFT}`}>
      {status}
    </span>
  );
}

function LoanStatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    DISBURSED: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    SANCTIONED: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
    REQUESTED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    OVERDUE: 'bg-red-50 text-red-700 ring-1 ring-red-600/20',
    CLOSED: 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20',
    REJECTED: 'bg-red-50 text-red-600 ring-1 ring-red-500/20',
    REPAYMENT_DUE: 'bg-orange-50 text-orange-700 ring-1 ring-orange-600/20',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${styles[status] || 'bg-slate-50 text-slate-600'}`}>
      {status.replaceAll('_', ' ')}
    </span>
  );
}

