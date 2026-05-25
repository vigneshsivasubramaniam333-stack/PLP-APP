import { useState, useEffect } from 'react';
import { anchorApi, portalApi, useAuth } from '@plp/shared';
import type { Anchor, Program, Invoice, Borrower } from '@plp/shared';

function isAnchorUser(linkedType: string | null | undefined): boolean {
  return (linkedType ?? '').trim().toUpperCase() === 'ANCHOR';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const anchorId =
    isAnchorUser(user?.linkedEntityType) && user?.linkedEntityId?.trim()
      ? user.linkedEntityId.trim()
      : '';

  const [anchorInfo, setAnchorInfo] = useState<Anchor | null>(null);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [employees, setEmployees] = useState<Borrower[]>([]);
  const [salaryRowCount, setSalaryRowCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!anchorId) {
      setLoading(false);
      setPrograms([]);
      setInvoices([]);
      setEmployees([]);
      setSalaryRowCount(null);
      setAnchorInfo(null);
      setError('');
      return;
    }

    let cancelled = false;
    const payPeriod = new Date().toISOString().slice(0, 7);

    (async () => {
      setLoading(true);
      setError('');
      try {
        const [progRes, invRes, empRes, anchorRes] = await Promise.all([
          portalApi.anchorPrograms(anchorId),
          portalApi.anchorInvoices(anchorId),
          portalApi.anchorEmployees(anchorId),
          anchorApi.get(anchorId),
        ]);

        let salaryCount: number | null = null;
        try {
          const salRes = await portalApi.anchorSalary(anchorId, payPeriod);
          const payload = salRes.data ?? salRes;
          const rows = Array.isArray(payload) ? payload : (payload as { data?: unknown })?.data ?? [];
          salaryCount = Array.isArray(rows) ? rows.length : 0;
        } catch {
          salaryCount = null;
        }

        if (cancelled) return;

        setPrograms(progRes.data?.data ?? []);
        setInvoices(invRes.data?.data ?? []);
        setEmployees(empRes.data?.data ?? []);
        setSalaryRowCount(salaryCount);
        const a = anchorRes.data?.data as Anchor | undefined;
        setAnchorInfo(a ?? null);
      } catch (err) {
        console.error('Failed to load dashboard data:', err);
        if (!cancelled) {
          setError('Could not load your dashboard. Try signing in again or contact support.');
          setPrograms([]);
          setInvoices([]);
          setEmployees([]);
          setSalaryRowCount(null);
          setAnchorInfo(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [anchorId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading dashboard...</div>
      </div>
    );
  }

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Anchor Dashboard</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your account is not linked to an anchor organisation. Contact support.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Anchor Dashboard</h1>
        <p className="mt-4 text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3">{error}</p>
      </div>
    );
  }

  const pdlPrograms = programs.filter((p) => p.productType === 'PAY_DAY_LOAN');
  const idPrograms = programs.filter((p) => p.productType === 'INVOICE_DISCOUNTING');

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  const recentInvoices = [...invoices].slice(0, 8);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Anchor Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1">
          Overview for{' '}
          <span className="font-medium text-slate-700">
            {anchorInfo?.entityName ?? anchorInfo?.anchorCode ?? 'your organisation'}
          </span>
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
                />
              </svg>
            </div>
            <div>
              <div className="text-2xl font-bold text-slate-800">{programs.length}</div>
              <div className="text-xs text-slate-500">Programs (your anchor)</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
                />
              </svg>
            </div>
            <div>
              <div className="text-2xl font-bold text-slate-800">{employees.length}</div>
              <div className="text-xs text-slate-500">Employees / borrowers</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
            </div>
            <div>
              <div className="text-2xl font-bold text-slate-800">{invoices.length}</div>
              <div className="text-xs text-slate-500">Invoices</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
            <div>
              <div className="text-2xl font-bold text-slate-800">
                {salaryRowCount === null ? '—' : salaryRowCount}
              </div>
              <div className="text-xs text-slate-500">Salary rows ({new Date().toISOString().slice(0, 7)})</div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center text-sm font-bold">
            PDL
          </div>
          <div>
            <div className="text-xl font-bold text-slate-800">{pdlPrograms.length}</div>
            <div className="text-xs text-slate-500">Pay Day Loan programs</div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center text-sm font-bold">
            ID
          </div>
          <div>
            <div className="text-xl font-bold text-slate-800">{idPrograms.length}</div>
            <div className="text-xs text-slate-500">Invoice discounting programs</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden mb-8">
        <div className="px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-700">Your programs</h3>
          <p className="text-xs text-slate-500 mt-0.5">Tenant-scoped to your linked anchor only</p>
        </div>
        {programs.length > 0 ? (
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Program
                </th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Product
                </th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Limit
                </th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {programs.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50/80">
                  <td className="px-5 py-3.5">
                    <div className="font-medium text-slate-800">{p.programName}</div>
                    <div className="text-xs text-slate-400 font-mono mt-0.5">{p.programCode}</div>
                  </td>
                  <td className="px-5 py-3.5">
                    <span
                      className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium ${
                        p.productType === 'PAY_DAY_LOAN' ? 'bg-sky-50 text-sky-700' : 'bg-purple-50 text-purple-700'
                      }`}
                    >
                      {p.productType === 'PAY_DAY_LOAN' ? 'Pay Day Loan' : 'Invoice Discounting'}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-right font-medium text-slate-700">{formatCurrency(p.programLimit)}</td>
                  <td className="px-5 py-3.5 text-center">
                    <span
                      className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold ${
                        p.status === 'ACTIVE'
                          ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                          : 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20'
                      }`}
                    >
                      {p.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="px-5 py-12 text-center">
            <div className="text-slate-400 text-sm">No programs found for your anchor</div>
            <p className="text-xs text-slate-400 mt-1">Programs are provisioned by your lender</p>
          </div>
        )}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center">
          <div>
            <h3 className="text-sm font-semibold text-slate-700">Recent invoices</h3>
            <p className="text-xs text-slate-500 mt-0.5">Latest rows for your anchor (up to 8)</p>
          </div>
          <span className="text-xs text-slate-400">{invoices.length} total</span>
        </div>
        {recentInvoices.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Invoice #
                  </th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Net
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {recentInvoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/80">
                    <td className="px-5 py-3 font-mono text-xs font-medium text-slate-800">{inv.invoiceNumber}</td>
                    <td className="px-5 py-3 text-right tabular-nums">{formatCurrency(inv.netAmount)}</td>
                    <td className="px-5 py-3 text-center">
                      <span className="inline-flex px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                        {inv.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="px-5 py-10 text-center text-slate-400 text-sm">No invoices yet</div>
        )}
      </div>
    </div>
  );
}
