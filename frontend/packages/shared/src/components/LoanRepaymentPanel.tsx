import { useCallback, useEffect, useState } from 'react';
import { loanApi } from '../api/client';
import type { Loan, LoanRepaymentRow } from '../types';
import { loanPrincipalAmount, resolveLmsAccountId } from '../utils/loanDisplay';
import { repaymentProgress } from '../utils/loanPayoff';

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(amount || 0);
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

function humanizeStatus(status: string | null | undefined): string {
  if (!status) return '—';
  return status.split('_').join(' ');
}

function statusBadgeClass(status: string | null | undefined): string {
  const s = (status ?? '').toUpperCase();
  if (s.includes('OVERDUE') || s === 'REJECTED') return 'bg-rose-50 text-rose-700 border-rose-200';
  if (s === 'CLOSED' || s === 'DISBURSED' || s.includes('DISCOUNTED'))
    return 'bg-emerald-50 text-emerald-700 border-emerald-200';
  if (s === 'SANCTIONED' || s === 'REPAYMENT_DUE') return 'bg-sky-50 text-sky-700 border-sky-200';
  return 'bg-slate-50 text-slate-600 border-slate-200';
}

type LoanRepaymentHistoryProps = {
  loanId: string;
  defaultExpanded?: boolean;
};

export function LoanRepaymentHistory({ loanId, defaultExpanded = false }: LoanRepaymentHistoryProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [repayments, setRepayments] = useState<LoanRepaymentRow[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await loanApi.listRepayments(loanId);
      setRepayments(res.data?.data ?? []);
    } catch {
      setError('Could not load repayment history.');
      setRepayments([]);
    } finally {
      setLoading(false);
    }
  }, [loanId]);

  useEffect(() => {
    if (defaultExpanded && repayments === null) {
      void load();
      setExpanded(true);
    }
  }, [defaultExpanded, load, repayments]);

  const toggle = async () => {
    const next = !expanded;
    setExpanded(next);
    if (next && repayments === null) {
      await load();
    }
  };

  return (
    <div className="border-t border-slate-100 px-4 py-3">
      <button
        type="button"
        onClick={() => void toggle()}
        className="flex w-full items-center justify-between text-left text-sm font-medium text-slate-800 hover:text-[var(--bt-orange,#ea580c)]"
      >
        <span>Repayment history</span>
        <span className="text-xs text-slate-400">{expanded ? 'Hide' : 'View'}</span>
      </button>
      {expanded ? (
        <div className="mt-3 space-y-3">
          {loading ? <p className="text-xs text-slate-500">Loading repayments…</p> : null}
          {error ? <p className="text-xs text-amber-700">{error}</p> : null}
          {!loading && repayments && repayments.length === 0 ? (
            <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-center text-xs text-slate-500">
              No repayments recorded yet for this loan.
            </p>
          ) : null}
          {!loading && repayments && repayments.length > 0 ? (
            <ol className="relative space-y-0 border-l border-slate-200 pl-4">
              {repayments.map((r, idx) => (
                <li key={r.id ?? idx} className="relative pb-4 last:pb-0">
                  <span className="absolute -left-[1.35rem] top-1 flex h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-white" />
                  <div className="rounded-lg border border-slate-100 bg-slate-50/80 px-3 py-2.5">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-sm font-semibold tabular-nums text-emerald-700">
                        {formatCurrency(r.amount)}
                      </span>
                      <span className="text-[11px] uppercase tracking-wide text-slate-400">
                        {r.paymentMode || r.status || 'Payment'}
                      </span>
                    </div>
                    <p className="mt-0.5 text-xs text-slate-500">{formatPaidAt(r.paidAt)}</p>
                    {r.reference ? (
                      <p className="mt-1 truncate font-mono text-[10px] text-slate-400">{r.reference}</p>
                    ) : null}
                  </div>
                </li>
              ))}
            </ol>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

type LoanSummaryWithRepaymentsProps = {
  loan: Loan;
  defaultExpandedHistory?: boolean;
};

export function LoanSummaryWithRepayments({ loan, defaultExpandedHistory = false }: LoanSummaryWithRepaymentsProps) {
  const principal = loanPrincipalAmount(loan);
  const repaid = Number(loan.totalRepaid ?? 0);
  const outstanding = Number(loan.outstandingAmount ?? 0);
  const progress = repaymentProgress(repaid, outstanding);
  const lmsAccountId = resolveLmsAccountId(loan);

  return (
    <article className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 bg-gradient-to-r from-slate-50 to-white px-4 py-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="font-mono text-sm font-semibold text-slate-800">{loan.loanNumber ?? 'Loan'}</h3>
            {lmsAccountId ? (
              <p className="mt-0.5 font-mono text-xs text-sky-700">
                LMS account <span className="font-semibold">{lmsAccountId}</span>
              </p>
            ) : null}
            <p className="mt-0.5 text-xs text-slate-500">Due {loan.dueDate ?? '—'}</p>
          </div>
          <span
            className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${statusBadgeClass(loan.status)}`}
          >
            {humanizeStatus(loan.status)}
          </span>
        </div>
      </div>

      <div className="grid gap-2 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Financed" value={formatCurrency(principal)} tone="slate" />
        <Stat label="Total repaid" value={formatCurrency(repaid)} tone="emerald" />
        <Stat label="Outstanding" value={formatCurrency(outstanding)} tone="rose" />
        <Stat label="Total obligation" value={formatCurrency(repaid + outstanding)} tone="sky" />
      </div>

      <div className="px-4 pb-3">
        <div className="mb-1 flex justify-between text-xs text-slate-500">
          <span>Repayment progress</span>
          <span className="font-medium tabular-nums text-slate-700">{progress.toFixed(0)}%</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
          <div
            className={`h-full rounded-full transition-all ${progress >= 100 ? 'bg-emerald-500' : 'bg-[var(--bt-orange,#ea580c)]'}`}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      <LoanRepaymentHistory loanId={loan.id} defaultExpanded={defaultExpandedHistory} />
    </article>
  );
}

type InvoiceLinkedLoansPanelProps = {
  invoiceId: string;
  defaultExpandedHistory?: boolean;
};

export function InvoiceLinkedLoansPanel({
  invoiceId,
  defaultExpandedHistory = true,
}: InvoiceLinkedLoansPanelProps) {
  const [loans, setLoans] = useState<Loan[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loanApi
      .list({ invoiceId })
      .then((res) => {
        if (!cancelled) setLoans(res.data?.data ?? []);
      })
      .catch(() => {
        if (!cancelled) {
          setError('Could not load loan details for this invoice.');
          setLoans([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [invoiceId]);

  if (loading) {
    return <p className="text-xs text-slate-500 py-2">Loading loan details…</p>;
  }
  if (error) {
    return <p className="text-xs text-amber-700 py-2">{error}</p>;
  }
  if (!loans || loans.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-center text-xs text-slate-500">
        No loan linked to this invoice yet.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {loans.map((loan) => (
        <LoanSummaryWithRepayments
          key={loan.id}
          loan={loan}
          defaultExpandedHistory={defaultExpandedHistory}
        />
      ))}
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: 'slate' | 'emerald' | 'rose' | 'sky';
}) {
  const tones = {
    slate: 'bg-slate-50 text-slate-800',
    emerald: 'bg-emerald-50 text-emerald-800',
    rose: 'bg-rose-50 text-rose-800',
    sky: 'bg-sky-50 text-sky-800',
  };
  const labelTones = {
    slate: 'text-slate-500',
    emerald: 'text-emerald-600',
    rose: 'text-rose-600',
    sky: 'text-sky-600',
  };
  return (
    <div className={`rounded-lg px-3 py-2.5 ${tones[tone]}`}>
      <div className={`text-[11px] font-medium uppercase tracking-wide ${labelTones[tone]}`}>{label}</div>
      <div className="mt-0.5 text-sm font-bold tabular-nums">{value}</div>
    </div>
  );
}
