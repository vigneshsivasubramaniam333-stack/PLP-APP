import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient, extractApiErrorMessage, loanApi, useAuth } from '@plp/shared';
import type { Loan } from '@plp/shared';

const REFRESH_STATS_EVENT = 'plp-borrower-loans-changed';

/** Lending-service may expose these even if omitted from minimal typings */
type LoanWithRepaymentTotals = Loan & {
  totalRepayable?: number;
  totalRepaid?: number;
};

function borrowerIdOnlyWhenBorrower(linkedType: string | undefined | null, linkedId: string | undefined | null): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
  return (linkedId ?? '').trim();
}

function canRepayLoan(status: string): boolean {
  return ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE'].includes(status);
}

export default function RepaymentHistoryPage() {
  const { user } = useAuth();
  const borrowerId = useMemo(
    () => borrowerIdOnlyWhenBorrower(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [loans, setLoans] = useState<LoanWithRepaymentTotals[]>([]);
  const [loading, setLoading] = useState(false);
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});
  const [repayingId, setRepayingId] = useState<string | null>(null);
  const [repayError, setRepayError] = useState('');

  const loadLoans = useCallback(() => {
    if (!borrowerId) {
      setLoans([]);
      return;
    }
    setLoading(true);
    loanApi
      .list({ borrowerId })
      .then((res) => {
        const allLoans = (res.data?.data || []) as LoanWithRepaymentTotals[];
        const filtered = allLoans.filter((l) =>
          ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE', 'CLOSED'].includes(String(l.status)),
        );
        setLoans(filtered);
        setRepayAmounts((prev) => {
          const next = { ...prev };
          for (const l of filtered) {
            if (canRepayLoan(String(l.status)) && next[l.id] === undefined) {
              next[l.id] = String(Number(l.outstandingAmount) || 0);
            }
          }
          return next;
        });
      })
      .catch(() => setLoans([]))
      .finally(() => setLoading(false));
  }, [borrowerId]);

  useEffect(() => {
    loadLoans();
  }, [loadLoans]);

  async function handleViewKfs(loanId: string) {
    try {
      const res = await apiClient.get<string>(`/api/v1/loans/${loanId}/kfs`, { responseType: 'text' });
      const html = res.data;
      const win = window.open('', '_blank');
      if (win) {
        win.document.write(html);
        win.document.close();
      }
    } catch (e) {
      console.error('Failed to load KFS', e);
    }
  }

  const handleRepay = async (loan: LoanWithRepaymentTotals) => {
    const raw = repayAmounts[loan.id];
    const amt = parseFloat(raw ?? '');
    if (Number.isNaN(amt) || amt <= 0) {
      setRepayError('Enter a valid repayment amount.');
      return;
    }
    setRepayingId(loan.id);
    setRepayError('');
    try {
      await loanApi.repay(loan.id, amt);
      loadLoans();
      window.dispatchEvent(new Event(REFRESH_STATS_EVENT));
    } catch (e: unknown) {
      setRepayError(extractApiErrorMessage(e, 'Repayment failed'));
    } finally {
      setRepayingId(null);
    }
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount || 0);

  if (!borrowerId) {
    return (
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-800">Repayment History</h1>
          <p className="text-sm text-slate-500 mt-1">Track your loan repayments and outstanding balances</p>
        </div>
        <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to a borrower record.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Repayment History</h1>
        <p className="text-sm text-slate-500 mt-1">Scoped to your borrower profile</p>
      </div>

      {repayError && (
        <div className="mb-4 p-3 rounded-lg text-sm bg-red-50 text-red-700 border border-red-200">{repayError}</div>
      )}

      {loading ? (
        <div className="flex items-center justify-center h-48">
          <div className="animate-pulse text-slate-400 text-sm">Loading repayment data...</div>
        </div>
      ) : loans.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div className="text-slate-400 text-sm">No repayment records</div>
          <p className="text-xs text-slate-400 mt-1">Disbursed loans and repayment details will appear here</p>
        </div>
      ) : (
        <div className="space-y-4">
          {loans.map((loan) => {
            const tp = loan.totalRepayable ?? loan.outstandingAmount ?? 0;
            const tr = loan.totalRepaid ?? 0;
            const progress = tp > 0 ? Math.min(100, (tr / tp) * 100) : 0;
            return (
              <div key={loan.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
                <div className="flex justify-between items-start mb-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-semibold text-slate-800">{loan.loanNumber}</span>
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                          loan.productType === 'PAY_DAY_LOAN' ? 'bg-sky-50 text-sky-700' : 'bg-purple-50 text-purple-700'
                        }`}
                      >
                        {loan.productType?.replaceAll('_', ' ')}
                      </span>
                    </div>
                  </div>
                  <span
                    className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold ${
                      loan.status === 'CLOSED'
                        ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                        : loan.status === 'OVERDUE'
                          ? 'bg-red-50 text-red-700 ring-1 ring-red-600/20'
                          : 'bg-sky-50 text-sky-700 ring-1 ring-sky-600/20'
                    }`}
                  >
                    {loan.status}
                  </span>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
                  <div className="bg-slate-50 rounded-lg p-3">
                    <div className="text-xs text-slate-500">Disbursed</div>
                    <div className="text-sm font-bold text-slate-800 mt-0.5">{formatCurrency(loan.disbursedAmount)}</div>
                  </div>
                  <div className="bg-slate-50 rounded-lg p-3">
                    <div className="text-xs text-slate-500">Total Repayable</div>
                    <div className="text-sm font-bold text-slate-800 mt-0.5">{formatCurrency(loan.totalRepayable ?? 0)}</div>
                  </div>
                  <div className="bg-emerald-50 rounded-lg p-3">
                    <div className="text-xs text-emerald-600">Repaid</div>
                    <div className="text-sm font-bold text-emerald-700 mt-0.5">{formatCurrency(loan.totalRepaid ?? 0)}</div>
                  </div>
                  <div className="bg-red-50 rounded-lg p-3">
                    <div className="text-xs text-red-600">Outstanding</div>
                    <div className="text-sm font-bold text-red-700 mt-0.5">{formatCurrency(loan.outstandingAmount)}</div>
                  </div>
                </div>

                <div className="mb-4">
                  <div className="flex justify-between text-xs mb-1.5">
                    <span className="text-slate-500">Repayment Progress</span>
                    <span className="font-semibold text-slate-700">{progress.toFixed(1)}%</span>
                  </div>
                  <div className="w-full bg-slate-100 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full ${progress >= 100 ? 'bg-emerald-500' : 'bg-sky-500'}`}
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                </div>

                <div className="flex flex-wrap justify-between items-center gap-3">
                  <span className="text-xs text-slate-500">Due: {loan.dueDate || 'N/A'}</span>
                  <div className="flex flex-wrap items-center gap-3">
                    {canRepayLoan(String(loan.status)) ? (
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          step="0.01"
                          min={0}
                          max={loan.outstandingAmount}
                          value={repayAmounts[loan.id] ?? ''}
                          onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                          className="w-28 px-2 py-1 border border-slate-200 rounded text-xs text-right"
                        />
                        <button
                          type="button"
                          disabled={repayingId === loan.id}
                          onClick={() => void handleRepay(loan)}
                          className="px-3 py-1.5 text-xs font-semibold bg-emerald-600 text-white rounded-md hover:bg-emerald-700 disabled:opacity-50"
                        >
                          {repayingId === loan.id ? 'Recording…' : 'Repay'}
                        </button>
                      </div>
                    ) : null}
                    <button
                      type="button"
                      onClick={() => void handleViewKfs(loan.id)}
                      className="text-xs font-semibold text-sky-600 hover:text-sky-700"
                    >
                      View KFS
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
