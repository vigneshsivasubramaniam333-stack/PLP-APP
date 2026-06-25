import { useEffect, useState } from 'react';
import { invoiceApi, notifyError, InvoiceListToolbar } from '@plp/shared';
import type { Invoice, InvoicePageMeta } from '@plp/shared';

export default function InvoicesPage() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [pageMeta, setPageMeta] = useState<InvoicePageMeta | null>(null);
  const [listFilters, setListFilters] = useState({
    search: '',
    status: '',
    lifecycle: 'active' as const,
    page: 0,
    size: 20,
  });
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    invoiceApi
      .list({
        search: listFilters.search || undefined,
        status: listFilters.status || undefined,
        lifecycle: listFilters.lifecycle,
        page: listFilters.page,
        size: listFilters.size,
      })
      .then((r) => {
        setInvoices(r.data.data || []);
        setPageMeta(r.data.page ?? null);
      })
      .catch((err) => {
        notifyError(err, 'Could not load invoices');
        setInvoices([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [listFilters]);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-800 mb-2">Invoices</h1>
      <p className="text-sm text-slate-500 mb-6">Invoice discounting lifecycle across all programs.</p>
      <InvoiceListToolbar filters={listFilters} onChange={(n) => setListFilters((f) => ({ ...f, ...n }))} pageMeta={pageMeta} />
      {loading ? (
        <p className="text-sm text-slate-400">Loading…</p>
      ) : invoices.length === 0 ? (
        <p className="text-sm text-slate-500">No invoices found.</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Invoice #</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Amount</th>
                <th className="px-4 py-3">Due</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="border-t border-slate-100">
                  <td className="px-4 py-3 font-mono text-xs">{inv.invoiceNumber}</td>
                  <td className="px-4 py-3">{inv.status?.replace(/_/g, ' ')}</td>
                  <td className="px-4 py-3 tabular-nums">{inv.netAmount ?? inv.invoiceAmount}</td>
                  <td className="px-4 py-3">{inv.dueDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
