import { useCallback, useEffect, useState } from 'react';
import {
  pgSettlementApi,
  notifyError,
  notifySuccess,
  type PaymentInProgressRow,
} from '@plp/shared';

export default function PgSettlementsPage() {
  const [pipRows, setPipRows] = useState<PaymentInProgressRow[]>([]);
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [utr, setUtr] = useState('');
  const [settlementDate, setSettlementDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [remarks, setRemarks] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pgSettlementApi.listOpenPip();
      setPipRows(res.data?.data ?? []);
    } catch (err) {
      notifyError(err, 'Could not load open PRUS lines');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const formatCurrency = (n: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n || 0);

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const applySettlement = async () => {
    if (selected.size === 0 || !utr.trim()) {
      notifyError(new Error('Select PRUS lines and enter settlement UTR'), 'Validation');
      return;
    }
    setSubmitting(true);
    try {
      await pgSettlementApi.createBatch({
        settlementDate,
        settlementUtr: utr.trim(),
        pipIds: Array.from(selected),
        remarks: remarks.trim() || undefined,
      });
      notifySuccess('Settlement applied and repayments posted');
      setSelected(new Set());
      setUtr('');
      await load();
    } catch (err) {
      notifyError(err, 'Settlement failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">PG settlements (PRUS)</h1>
        <p className="text-sm text-slate-500 mt-1">
          Open payment-in-progress lines from successful PayU collections. Apply settlement with bank UTR to post loan
          repayments.
        </p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
        <h2 className="text-sm font-semibold text-slate-700 mb-3">Create settlement batch</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Settlement date</label>
            <input
              type="date"
              value={settlementDate}
              onChange={(e) => setSettlementDate(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Settlement UTR</label>
            <input
              type="text"
              value={utr}
              onChange={(e) => setUtr(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              placeholder="Bank UTR / reference"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Remarks (optional)</label>
            <input
              type="text"
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </div>
        </div>
        <button
          type="button"
          disabled={submitting || selected.size === 0}
          onClick={() => void applySettlement()}
          className="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50"
        >
          {submitting ? 'Applying…' : `Apply settlement (${selected.size} selected)`}
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/80">
          <span className="text-xs font-semibold text-slate-500 uppercase">Open PRUS / PIP</span>
        </div>
        {loading ? (
          <p className="p-8 text-sm text-slate-400 text-center">Loading…</p>
        ) : pipRows.length === 0 ? (
          <p className="p-8 text-sm text-slate-400 text-center">No open payment-in-progress lines.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-4 py-2 w-10" />
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase">Invoice</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase">Borrower</th>
                  <th className="px-4 py-2 text-right text-xs font-semibold text-slate-500 uppercase">Amount</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pipRows.map((row) => (
                  <tr key={row.id} className="hover:bg-slate-50/50">
                    <td className="px-4 py-2 text-center">
                      <input
                        type="checkbox"
                        checked={selected.has(row.id)}
                        onChange={() => toggle(row.id)}
                      />
                    </td>
                    <td className="px-4 py-2 font-mono text-xs">{row.invoiceId}</td>
                    <td className="px-4 py-2 font-mono text-xs">{row.borrowerId}</td>
                    <td className="px-4 py-2 text-right font-medium">{formatCurrency(row.principalAmount)}</td>
                    <td className="px-4 py-2 text-xs text-slate-500">
                      {row.createdAt ? new Date(row.createdAt).toLocaleString() : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
