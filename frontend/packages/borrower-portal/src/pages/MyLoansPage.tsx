import { useCallback, useEffect, useMemo, useState } from 'react';
import { portalApi, loanApi, extractApiErrorMessage, useAuth } from '@plp/shared';
import type { Loan } from '@plp/shared';

const REFRESH_STATS_EVENT = 'plp-borrower-loans-changed';

function borrowerIdFromAuth(
  linkedType: string | null | undefined,
  linkedId: string | null | undefined,
): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
  return (linkedId ?? '').trim();
}

function canBorrowerRepay(status: string): boolean {
  return ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE'].includes(status);
}

export default function MyLoansPage() {
  const { user } = useAuth();
  const borrowerId = useMemo(
    () => borrowerIdFromAuth(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadedOnce, setLoadedOnce] = useState(false);
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});
  const [repayingId, setRepayingId] = useState<string | null>(null);
  const [repayMsg, setRepayMsg] = useState('');

  const fetchLoans = useCallback(async () => {
    if (!borrowerId) {
      setLoans([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const r = await portalApi.borrowerLoans(borrowerId);
      const rows = (r.data.data || []) as Loan[];
      setLoans(rows);
      setRepayAmounts((prev) => {
        const next = { ...prev };
        for (const l of rows) {
          if (canBorrowerRepay(l.status) && next[l.id] === undefined) {
            next[l.id] = String(Number(l.outstandingAmount) || 0);
          }
        }
        return next;
      });
      setLoadedOnce(true);
    } catch (err) {
      console.error('Failed to fetch loans:', err);
      setLoans([]);
      setLoadedOnce(true);
    } finally {
      setLoading(false);
    }
  }, [borrowerId]);

  useEffect(() => {
    void fetchLoans();
  }, [fetchLoans]);

  const handleRepay = async (loan: Loan) => {
    const raw = repayAmounts[loan.id];
    const amount = parseFloat(raw ?? '');
    if (Number.isNaN(amount) || amount <= 0) {
      setRepayMsg('Enter a valid repayment amount.');
      return;
    }
    setRepayingId(loan.id);
    setRepayMsg('');
    try {
      await loanApi.repay(loan.id, amount);
      setRepayMsg('Repayment recorded successfully.');
      await fetchLoans();
      window.dispatchEvent(new Event(REFRESH_STATS_EVENT));
    } catch (err: unknown) {
      setRepayMsg(extractApiErrorMessage(err, 'Repayment failed'));
    } finally {
      setRepayingId(null);
    }
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  if (!borrowerId) {
    return (
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-800">My Loans</h1>
          <p className="text-sm text-slate-500 mt-1">View all your loan applications and active loans</p>
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
        <h1 className="text-2xl font-bold text-slate-800">My Loans</h1>
        <p className="text-sm text-slate-500 mt-1">Scoped to your profile — loans load automatically</p>
      </div>

      {repayMsg && (
        <div
          className={`mb-4 p-3 rounded-lg text-sm ${
            repayMsg.includes('fail') || repayMsg.startsWith('Enter')
              ? 'bg-red-50 text-red-700 border border-red-200'
              : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
          }`}
        >
          {repayMsg}
        </div>
      )}

      {loading && !loadedOnce ? (
        <div className="flex justify-center py-16">
          <div className="animate-pulse text-slate-400 text-sm">Loading loans…</div>
        </div>
      ) : loans.length > 0 ? (
        <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Loan #
                </th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Product
                </th>
                <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Amount
                </th>
                <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Outstanding
                </th>
                <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Tenure
                </th>
                <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Due Date
                </th>
                <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Repay
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loans.map((loan) => (
                <tr key={loan.id} className="hover:bg-slate-50/80">
                  <td className="px-5 py-3.5 font-mono text-xs font-medium text-slate-700">{loan.loanNumber}</td>
                  <td className="px-5 py-3.5">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                        loan.productType === 'PAY_DAY_LOAN' ? 'bg-sky-50 text-sky-700' : 'bg-purple-50 text-purple-700'
                      }`}
                    >
                      {loan.productType === 'PAY_DAY_LOAN' ? 'PDL' : 'ID'}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-right font-medium text-slate-700">{formatCurrency(loan.requestedAmount)}</td>
                  <td className="px-5 py-3.5 text-right font-medium text-slate-700">{formatCurrency(loan.outstandingAmount)}</td>
                  <td className="px-5 py-3.5 text-center text-slate-600">{loan.tenureDays}d</td>
                  <td className="px-5 py-3.5 text-center">
                    <LoanBadge status={loan.status} />
                  </td>
                  <td className="px-5 py-3.5 text-xs text-slate-500">{loan.dueDate || '—'}</td>
                  <td className="px-5 py-3.5 text-center">
                    {canBorrowerRepay(loan.status) ? (
                      <div className="flex flex-col items-stretch gap-1.5 min-w-[140px]">
                        <input
                          type="number"
                          step="0.01"
                          min={0}
                          max={loan.outstandingAmount}
                          value={repayAmounts[loan.id] ?? ''}
                          onChange={(e) =>
                            setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))
                          }
                          className="px-2 py-1 border border-slate-200 rounded text-xs text-right"
                        />
                        <button
                          type="button"
                          disabled={repayingId === loan.id}
                          onClick={() => void handleRepay(loan)}
                          className="px-2.5 py-1.5 text-xs font-semibold bg-emerald-600 text-white rounded-md hover:bg-emerald-700 disabled:opacity-50"
                        >
                          {repayingId === loan.id ? 'Recording…' : 'Repay'}
                        </button>
                      </div>
                    ) : (
                      <span className="text-slate-400">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div className="text-slate-400 text-sm">No loans found</div>
        </div>
      )}
    </div>
  );
}

function LoanBadge({ status }: { status: string }) {
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
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${styles[status] || 'bg-slate-50 text-slate-600'}`}>
      {status.replaceAll('_', ' ')}
    </span>
  );
}
