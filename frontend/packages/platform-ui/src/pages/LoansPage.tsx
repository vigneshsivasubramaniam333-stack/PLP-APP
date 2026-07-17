import { useEffect, useState } from 'react';
import {
  getStoredAuthUser,
  lenderLoanCapabilities,
  loanApi,
  loanHasLmsAccount,
  loanPrincipalAmount,
  resolveLmsAccountId,
  LoanSummaryWithRepayments,
  notifyError,
  notifySuccess,
  notifyErrorMessage,
} from '@plp/shared';
import type { Loan } from '@plp/shared';

function humanizeStatus(status: string): string {
  return status.split('_').join(' ');
}

function parsePendingDisburseAmount(loan: Loan): number {
  const snap = loan.eligibilitySnapshot;
  if (snap && snap.pendingDisbursementAmount != null) {
    const v = snap.pendingDisbursementAmount;
    return typeof v === 'number' ? v : Number(v);
  }
  return loan.sanctionedAmount ?? loan.requestedAmount;
}

const REPAY_ELIGIBLE_STATUSES = ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE'] as const;

function defaultRepaymentAmountInput(loan: Loan): string {
  const o = loan.outstandingAmount;
  if (o != null && Number.isFinite(o) && o > 0) return String(o);
  return '';
}

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loanTab, setLoanTab] = useState<'active' | 'closed'>('active');
  const [loading, setLoading] = useState(true);
  const [repayModalLoan, setRepayModalLoan] = useState<Loan | null>(null);
  const [repayAmount, setRepayAmount] = useState('');
  const [repaySubmitting, setRepaySubmitting] = useState(false);
  const [sanctionModalLoan, setSanctionModalLoan] = useState<Loan | null>(null);
  const [sanctionAmount, setSanctionAmount] = useState('');
  const [sanctionDate, setSanctionDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [sanctionSubmitting, setSanctionSubmitting] = useState(false);
  const [expandedClosedLoanIds, setExpandedClosedLoanIds] = useState<Set<string>>(() => new Set());

  const caps = lenderLoanCapabilities(getStoredAuthUser()?.role);

  const reload = () => {
    loanApi.list().then((res) => setLoans(res.data.data || [])).catch((err) => notifyError(err, 'Could not load loans'));
  };

  useEffect(() => {
    loanApi
      .list()
      .then((res) => {
        setLoans(res.data.data || []);
      })
      .catch((err) => notifyError(err, 'Could not load loans'))
      .finally(() => setLoading(false));
  }, []);

  const visibleLoans = loans.filter((l) =>
    loanTab === 'closed' ? ['CLOSED', 'REJECTED'].includes(l.status) : !['CLOSED', 'REJECTED'].includes(l.status),
  );

  const handleSanction = async (loan: Loan) => {
    setSanctionAmount(String(loan.requestedAmount ?? ''));
    setSanctionDate(new Date().toISOString().slice(0, 10));
    setSanctionModalLoan(loan);
  };

  const closeSanctionModal = () => {
    if (sanctionSubmitting) return;
    setSanctionModalLoan(null);
    setSanctionAmount('');
  };

  const handleSubmitSanction = async () => {
    if (!sanctionModalLoan) return;
    const amount = Number.parseFloat(sanctionAmount.replace(/,/g, ''));
    if (!Number.isFinite(amount) || amount <= 0) {
      notifyErrorMessage('Enter a valid sanction amount greater than zero');
      return;
    }
    if (!sanctionDate) {
      notifyErrorMessage('Select a sanction date');
      return;
    }
    setSanctionSubmitting(true);
    try {
      await loanApi.approve(sanctionModalLoan.id, {
        sanctionedAmount: amount,
        sanctionDate,
      });
      notifySuccess(`Loan ${sanctionModalLoan.loanNumber} sanctioned`);
      setSanctionModalLoan(null);
      setSanctionAmount('');
      reload();
    } catch (err) {
      notifyError(err, 'Sanction failed');
    } finally {
      setSanctionSubmitting(false);
    }
  };

  const toggleClosedLoanDetails = (loanId: string) => {
    setExpandedClosedLoanIds((prev) => {
      const next = new Set(prev);
      if (next.has(loanId)) next.delete(loanId);
      else next.add(loanId);
      return next;
    });
  };

  const handleInitiateDisbursement = async (loan: Loan) => {
    try {
      const amount = loan.sanctionedAmount ?? loan.requestedAmount;
      await loanApi.initiateDisbursement(loan.id, amount);
      notifySuccess(`Disbursement initiated for ${loan.loanNumber}`);
      reload();
    } catch (err) {
      notifyError(err, 'Initiate disbursement failed');
    }
  };

  const handleApproveDisbursement = async (loan: Loan) => {
    try {
      const amount = parsePendingDisburseAmount(loan);
      await loanApi.disburse(loan.id, amount);
      notifySuccess(`Loan ${loan.loanNumber} disbursement approved`);
      reload();
    } catch (err) {
      notifyError(err, 'Approve disbursement failed');
    }
  };

  const handleCancelDisbursement = async (loan: Loan) => {
    try {
      await loanApi.cancelDisbursement(loan.id);
      notifySuccess('Disbursement cancelled successfully');
      reload();
    } catch (err) {
      notifyError(err, 'Cancel disbursement failed');
    }
  };

  const handleReject = async (loan: Loan) => {
    try {
      await loanApi.reject(loan.id, 'Rejected by lender');
      notifySuccess(`Loan ${loan.loanNumber} rejected`);
      reload();
    } catch (err) {
      notifyError(err, 'Reject failed');
    }
  };

  const openRepayModal = (loan: Loan) => {
    setRepayAmount(defaultRepaymentAmountInput(loan));
    setRepayModalLoan(loan);
  };

  const closeRepayModal = () => {
    if (repaySubmitting) return;
    setRepayModalLoan(null);
    setRepayAmount('');
  };

  const handleSubmitRepayment = async () => {
    if (!repayModalLoan) return;
    const amount = Number.parseFloat(repayAmount.replace(/,/g, ''));
    if (!Number.isFinite(amount) || amount <= 0) {
      notifyErrorMessage('Enter a valid repayment amount greater than zero');
      return;
    }
    setRepaySubmitting(true);
    try {
      await loanApi.repay(repayModalLoan.id, amount);
      notifySuccess(`Repayment recorded for ${repayModalLoan.loanNumber}`);
      setRepayModalLoan(null);
      setRepayAmount('');
      reload();
    } catch (err) {
      notifyError(err, 'Repayment failed');
    } finally {
      setRepaySubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading loans...</div>
      </div>
    );
  }

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
      amount || 0,
    );

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Loans</h1>
        <p className="text-sm text-slate-500 mt-1">All loan applications and active loans</p>
        <div className="flex gap-2 mt-4">
          {(['active', 'closed'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setLoanTab(tab)}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium capitalize ${
                loanTab === tab ? 'bg-[var(--bt-orange)] text-white' : 'bg-slate-100 text-slate-600'
              }`}
            >
              {tab === 'active' ? 'Active' : 'Closed'}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        {[
          { label: 'Total', value: loans.length, color: 'text-slate-700' },
          {
            label: 'Active',
            value: loans.filter((l) => ['DISBURSED', 'REPAYMENT_DUE'].includes(l.status)).length,
            color: 'text-emerald-600',
          },
          {
            label: 'Pending',
            value: loans.filter((l) =>
              ['REQUESTED', 'SANCTIONED', 'DISBURSEMENT_PENDING'].includes(l.status),
            ).length,
            color: 'text-amber-600',
          },
          { label: 'Overdue', value: loans.filter((l) => l.status === 'OVERDUE').length, color: 'text-red-600' },
        ].map((s) => (
          <div key={s.label} className="bg-white rounded-lg border border-slate-200 px-4 py-3">
            <div className={`text-xl font-bold ${s.color}`}>{s.value}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200">
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Loan
              </th>
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Invoice
              </th>
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Product
              </th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Amount
              </th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Rate
              </th>
              <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Tenure
              </th>
              <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Status
              </th>
              <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visibleLoans.flatMap((l) => {
              const showSanctionReject = l.status === 'REQUESTED' && caps.canSanctionOrReject;
              const showInitiate = l.status === 'SANCTIONED' && caps.canInitiateDisburse;
              const showApproveDisburse = l.status === 'DISBURSEMENT_PENDING' && caps.canApproveDisburse;
              const showCancelDisburse = l.status === 'DISBURSEMENT_PENDING' && caps.canCancelDisburse;
              const showRecordRepayment =
                caps.canRecordRepayment && (REPAY_ELIGIBLE_STATUSES as readonly string[]).includes(l.status);
              const showClosedRepayments =
                loanTab === 'closed' && ['CLOSED', 'REJECTED'].includes(l.status);
              const hasActions =
                showSanctionReject ||
                showInitiate ||
                showApproveDisburse ||
                showCancelDisburse ||
                showRecordRepayment ||
                showClosedRepayments;
              const showStatusHint =
                !hasActions &&
                ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE', 'CLOSED', 'CANCELLED'].includes(l.status);

              const rows = [
              <tr key={l.id} className="hover:bg-slate-50/80">
                <td className="px-5 py-3.5">
                  <div className="font-mono text-xs font-medium text-slate-700">{l.loanNumber}</div>
                  {loanHasLmsAccount(l) ? (
                    <div className="mt-0.5 font-mono text-[11px] text-sky-700">
                      LMS {resolveLmsAccountId(l)}
                    </div>
                  ) : null}
                  <div className="text-[11px] text-slate-400 mt-0.5">{l.requestDate}</div>
                </td>
                <td className="px-5 py-3.5 font-mono text-xs text-slate-600">
                  {l.invoiceNumber || '—'}
                </td>
                <td className="px-5 py-3.5">
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                      l.productType === 'PAY_DAY_LOAN' ? 'bg-blue-50 text-blue-700' : 'bg-purple-50 text-purple-700'
                    }`}
                  >
                    {l.productType === 'PAY_DAY_LOAN' ? 'PDL' : 'ID'}
                  </span>
                </td>
                <td className="px-5 py-3.5 text-right font-medium text-slate-700">
                  {formatCurrency(loanPrincipalAmount(l))}
                </td>
                <td className="px-5 py-3.5 text-right text-slate-600">{l.interestRate}%</td>
                <td className="px-5 py-3.5 text-center text-slate-600">{l.tenureDays}d</td>
                <td className="px-5 py-3.5 text-center">
                  <LoanStatusBadge status={l.status} />
                </td>
                <td className="px-5 py-3.5 text-center">
                  <div className="flex items-center justify-center gap-1.5 flex-wrap">
                    {showSanctionReject && (
                      <>
                        <button
                          type="button"
                          onClick={() => handleSanction(l)}
                          className="px-2.5 py-1 text-xs font-semibold text-emerald-700 bg-emerald-50 rounded hover:bg-emerald-100"
                        >
                          Sanction
                        </button>
                        <button
                          type="button"
                          onClick={() => handleReject(l)}
                          className="px-2.5 py-1 text-xs font-semibold text-red-700 bg-red-50 rounded hover:bg-red-100"
                        >
                          Reject
                        </button>
                      </>
                    )}
                    {showInitiate && (
                      <button
                        type="button"
                        onClick={() => handleInitiateDisbursement(l)}
                        className="px-2.5 py-1 text-xs font-semibold text-indigo-700 bg-indigo-50 rounded hover:bg-indigo-100"
                      >
                        Initiate Disbursement
                      </button>
                    )}
                    {showApproveDisburse && (
                      <button
                        type="button"
                        onClick={() => handleApproveDisbursement(l)}
                        className="px-2.5 py-1 text-xs font-semibold text-blue-700 bg-blue-50 rounded hover:bg-blue-100"
                      >
                        Approve Disbursement
                      </button>
                    )}
                    {showCancelDisburse && (
                      <button
                        type="button"
                        onClick={() => void handleCancelDisbursement(l)}
                        className="px-2.5 py-1 text-xs font-semibold text-white bg-red-600 rounded hover:bg-red-700"
                      >
                        Cancel Disbursement
                      </button>
                    )}
                    {showRecordRepayment && (
                      <button
                        type="button"
                        onClick={() => openRepayModal(l)}
                        className="px-2.5 py-1 text-xs font-semibold text-teal-700 bg-teal-50 rounded hover:bg-teal-100"
                      >
                        Record Repayment
                      </button>
                    )}
                    {showClosedRepayments && (
                      <button
                        type="button"
                        onClick={() => toggleClosedLoanDetails(l.id)}
                        className="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-slate-100 rounded hover:bg-slate-200"
                      >
                        {expandedClosedLoanIds.has(l.id) ? 'Hide repayments' : 'View repayments'}
                      </button>
                    )}
                    {!hasActions && (
                      <span className="text-xs text-slate-400">
                        {showStatusHint ? l.dueDate || '—' : '—'}
                      </span>
                    )}
                  </div>
                </td>
              </tr>,
              ];

              if (showClosedRepayments && expandedClosedLoanIds.has(l.id)) {
                rows.push(
                  <tr key={`${l.id}-repayments`}>
                    <td colSpan={8} className="bg-slate-50/60 px-5 py-4 align-top">
                      <LoanSummaryWithRepayments loan={l} defaultExpandedHistory />
                    </td>
                  </tr>,
                );
              }

              return rows;
            })}
            {loans.length === 0 && (
              <tr>
                <td colSpan={8} className="px-5 py-12 text-center">
                  <div className="text-slate-400 text-sm">No loans found</div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {sanctionModalLoan && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40"
          role="presentation"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeSanctionModal();
          }}
        >
          <div
            className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-md p-5"
            role="dialog"
            aria-labelledby="sanction-modal-title"
          >
            <h2 id="sanction-modal-title" className="text-lg font-semibold text-slate-800">
              Sanction loan
            </h2>
            <p className="text-xs text-slate-500 mt-1 font-mono">{sanctionModalLoan.loanNumber}</p>
            <label className="block mt-4">
              <span className="text-xs font-medium text-slate-600">Sanction amount (₹)</span>
              <input
                type="number"
                min={0}
                step="0.01"
                value={sanctionAmount}
                onChange={(e) => setSanctionAmount(e.target.value)}
                className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:ring-2 focus:ring-emerald-500/30 focus:border-emerald-500 outline-none"
                placeholder="Amount"
                disabled={sanctionSubmitting}
              />
            </label>
            <label className="block mt-4">
              <span className="text-xs font-medium text-slate-600">Sanction date</span>
              <input
                type="date"
                value={sanctionDate}
                onChange={(e) => setSanctionDate(e.target.value)}
                className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:ring-2 focus:ring-emerald-500/30 focus:border-emerald-500 outline-none"
                disabled={sanctionSubmitting}
              />
            </label>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeSanctionModal}
                disabled={sanctionSubmitting}
                className="px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 rounded-lg disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void handleSubmitSanction()}
                disabled={sanctionSubmitting}
                className="px-3 py-1.5 text-sm font-semibold text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:opacity-50"
              >
                {sanctionSubmitting ? 'Sanctioning…' : 'Sanction'}
              </button>
            </div>
          </div>
        </div>
      )}

      {repayModalLoan && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40"
          role="presentation"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeRepayModal();
          }}
        >
          <div
            className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-md p-5"
            role="dialog"
            aria-labelledby="repay-modal-title"
          >
            <h2 id="repay-modal-title" className="text-lg font-semibold text-slate-800">
              Record repayment
            </h2>
            <p className="text-xs text-slate-500 mt-1 font-mono">{repayModalLoan.loanNumber}</p>
            <label className="block mt-4">
              <span className="text-xs font-medium text-slate-600">Repayment amount (₹)</span>
              <input
                type="number"
                min={0}
                step="0.01"
                value={repayAmount}
                onChange={(e) => setRepayAmount(e.target.value)}
                className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 outline-none"
                placeholder="Amount"
                disabled={repaySubmitting}
              />
            </label>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeRepayModal}
                disabled={repaySubmitting}
                className="px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 rounded-lg disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void handleSubmitRepayment()}
                disabled={repaySubmitting}
                className="px-3 py-1.5 text-sm font-semibold text-white bg-teal-600 rounded-lg hover:bg-teal-700 disabled:opacity-50"
              >
                {repaySubmitting ? 'Recording…' : 'Submit'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function LoanStatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    DISBURSED: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    SANCTIONED: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
    DISBURSEMENT_PENDING: 'bg-violet-50 text-violet-800 ring-1 ring-violet-600/20',
    REQUESTED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    OVERDUE: 'bg-red-50 text-red-700 ring-1 ring-red-600/20',
    CLOSED: 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20',
    CANCELLED: 'bg-stone-100 text-stone-700 ring-1 ring-stone-400/35',
    REJECTED: 'bg-red-50 text-red-600 ring-1 ring-red-500/20',
    REPAYMENT_DUE: 'bg-orange-50 text-orange-700 ring-1 ring-orange-600/20',
  };
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${styles[status] || 'bg-slate-50 text-slate-600'}`}
    >
      {humanizeStatus(status)}
    </span>
  );
}
