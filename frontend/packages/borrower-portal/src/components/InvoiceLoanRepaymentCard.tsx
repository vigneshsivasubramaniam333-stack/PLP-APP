import { useCallback, useState } from 'react';
import { loanApi, loanPrincipalAmount, repaymentProgress } from '@plp/shared';
import type { Invoice, Loan, LoanRepaymentRow } from '@plp/shared';

function money(n: number | null | undefined): string {
  if (n == null || Number.isNaN(Number(n))) return '—';
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
}

function formatPaidAt(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

type Props = {
  invoice: Invoice;
  loan: Loan;
  onRepay?: (loanId: string, amount: number) => Promise<void>;
  repaying?: boolean;
};

export function InvoiceLoanRepaymentCard({ invoice, loan, onRepay, repaying }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [repayments, setRepayments] = useState<LoanRepaymentRow[] | null>(null);
  const [loadingRepayments, setLoadingRepayments] = useState(false);
  const [repayErr, setRepayErr] = useState('');
  const [repayAmount, setRepayAmount] = useState(
    String(Number(loan.outstandingAmount) > 0 ? loan.outstandingAmount : ''),
  );

  const repaid = Number(loan.totalRepaid ?? 0);
  const outstanding = Number(loan.outstandingAmount ?? 0);
  const progress = repaymentProgress(repaid, outstanding);
  const canRepay = onRepay && ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE'].includes(String(loan.status));

  const loadRepayments = useCallback(async () => {
    setLoadingRepayments(true);
    setRepayErr('');
    try {
      const res = await loanApi.listRepayments(loan.id);
      const rows = (res.data?.data ?? []) as LoanRepaymentRow[];
      setRepayments(rows);
    } catch {
      setRepayments([]);
      setRepayErr('Could not load repayment history.');
    } finally {
      setLoadingRepayments(false);
    }
  }, [loan.id]);

  async function toggleHistory() {
    const next = !expanded;
    setExpanded(next);
    if (next && repayments === null) {
      await loadRepayments();
    }
  }

  async function submitRepay(e: React.FormEvent) {
    e.preventDefault();
    if (!onRepay) return;
    const amt = parseFloat(repayAmount);
    if (Number.isNaN(amt) || amt <= 0) {
      setRepayErr('Enter a valid repayment amount.');
      return;
    }
    setRepayErr('');
    try {
      await onRepay(loan.id, amt);
      setRepayments(null);
      if (expanded) await loadRepayments();
    } catch {
      setRepayErr('Repayment failed. Please try again.');
    }
  }

  return (
    <article className="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white shadow-sm">
      <div className="border-b border-slate-100 px-4 py-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Linked loan</p>
            <p className="font-mono text-sm font-medium text-slate-800">{loan.loanNumber}</p>
            <p className="text-xs text-slate-500">Invoice {invoice.invoiceNumber}</p>
          </div>
          <span className="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 text-xs font-medium text-slate-700">
            {loan.status}
          </span>
        </div>
      </div>
      <div className="grid gap-3 px-4 py-3 sm:grid-cols-3">
        <div>
          <p className="text-xs text-slate-500">Disbursed</p>
          <p className="font-medium text-slate-800">{money(loanPrincipalAmount(loan))}</p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Repaid</p>
          <p className="font-medium text-emerald-700">{money(repaid)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Outstanding</p>
          <p className="font-medium text-amber-800">{money(outstanding)}</p>
        </div>
      </div>
      <div className="px-4 pb-3">
        <div className="h-2 overflow-hidden rounded-full bg-slate-200">
          <div className="h-full rounded-full bg-emerald-500 transition-all" style={{ width: `${progress}%` }} />
        </div>
        <p className="mt-1 text-xs text-slate-500">{progress.toFixed(0)}% repaid</p>
      </div>
      {canRepay ? (
        <form onSubmit={(e) => void submitRepay(e)} className="flex flex-wrap items-end gap-2 border-t border-slate-100 px-4 py-3">
          <label className="min-w-[140px] flex-1 text-sm">
            <span className="mb-1 block text-xs text-slate-500">Repay amount</span>
            <input
              className="bt-input w-full"
              value={repayAmount}
              onChange={(e) => setRepayAmount(e.target.value)}
              type="number"
              min="0"
              step="0.01"
            />
          </label>
          <button type="submit" disabled={repaying} className="bt-btn bt-btn-primary bt-btn-sm">
            {repaying ? 'Processing…' : 'Repay'}
          </button>
        </form>
      ) : null}
      {repayErr ? <p className="px-4 pb-2 text-xs text-red-600">{repayErr}</p> : null}
      <div className="border-t border-slate-100 px-4 py-2">
        <button type="button" onClick={() => void toggleHistory()} className="text-sm font-medium text-blue-700 hover:underline">
          {expanded ? 'Hide repayment history' : 'View repayment history'}
        </button>
        {expanded ? (
          <div className="mt-2 pb-3">
            {loadingRepayments ? (
              <p className="text-xs text-slate-500">Loading repayments…</p>
            ) : repayments && repayments.length > 0 ? (
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-100 bg-white">
                {repayments.map((r) => (
                  <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                    <span className="font-medium text-slate-800">{money(r.amount)}</span>
                    <span className="text-xs text-slate-500">{formatPaidAt(r.paidAt)}</span>
                    {r.paymentMode ? <span className="text-xs text-slate-400">{r.paymentMode}</span> : null}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-slate-500">No repayments recorded yet.</p>
            )}
          </div>
        ) : null}
      </div>
    </article>
  );
}
