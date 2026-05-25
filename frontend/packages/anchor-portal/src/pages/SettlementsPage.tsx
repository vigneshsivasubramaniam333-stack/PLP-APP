import { useState, useEffect } from 'react';
import { loanApi } from '@plp/shared';

interface Loan {
  id: string;
  loanNumber: string;
  productType: string;
  borrowerId: string;
  requestedAmount: number;
  disbursedAmount: number;
  outstandingAmount: number;
  totalRepaid: number;
  status: string;
  dueDate: string;
  requestDate: string;
}

export default function SettlementsPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => { loadLoans(); }, []);

  async function loadLoans() {
    setLoading(true);
    try {
      const res = await loanApi.list();
      const allLoans: Loan[] = res.data?.data || [];
      setLoans(allLoans.filter(l =>
        ['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE', 'CLOSED'].includes(l.status)
      ));
    } catch { setLoans([]); }
    setLoading(false);
  }

  const totalDisbursed = loans.reduce((s, l) => s + (l.disbursedAmount || 0), 0);
  const totalOutstanding = loans.reduce((s, l) => s + (l.outstandingAmount || 0), 0);
  const totalRepaid = loans.reduce((s, l) => s + (l.totalRepaid || 0), 0);
  const activeCount = loans.filter(l => l.status !== 'CLOSED').length;

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Settlement Tracking</h1>
        <p className="text-sm text-slate-500 mt-1">Monitor loan disbursements, repayments, and outstanding amounts</p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <div>
              <div className="text-2xl font-bold text-slate-800">{activeCount}</div>
              <div className="text-xs text-slate-500">Active Loans</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <div className="text-lg font-bold text-slate-800">{formatCurrency(totalDisbursed)}</div>
              <div className="text-xs text-slate-500">Total Disbursed</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <div className="text-lg font-bold text-emerald-600">{formatCurrency(totalRepaid)}</div>
              <div className="text-xs text-slate-500">Total Repaid</div>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-red-50 text-red-600 flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <div className="text-lg font-bold text-red-600">{formatCurrency(totalOutstanding)}</div>
              <div className="text-xs text-slate-500">Outstanding</div>
            </div>
          </div>
        </div>
      </div>

      {/* Loans Table */}
      {loading ? (
        <div className="flex items-center justify-center h-48">
          <div className="animate-pulse text-slate-400 text-sm">Loading settlements...</div>
        </div>
      ) : loans.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div className="text-slate-400 text-sm">No disbursed loans to track</div>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Loan #</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Product</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Disbursed</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Repaid</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Outstanding</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Due Date</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loans.map(l => (
                <tr key={l.id} className="hover:bg-slate-50/80">
                  <td className="px-5 py-3.5 font-mono text-xs font-medium text-slate-700">{l.loanNumber}</td>
                  <td className="px-5 py-3.5">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                      l.productType === 'PAY_DAY_LOAN' ? 'bg-sky-50 text-sky-700' : 'bg-purple-50 text-purple-700'
                    }`}>
                      {l.productType?.replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-right text-slate-700">{formatCurrency(l.disbursedAmount)}</td>
                  <td className="px-5 py-3.5 text-right text-emerald-600 font-medium">{formatCurrency(l.totalRepaid || 0)}</td>
                  <td className="px-5 py-3.5 text-right text-red-600 font-medium">{formatCurrency(l.outstandingAmount)}</td>
                  <td className="px-5 py-3.5 text-xs text-slate-500">{l.dueDate || 'N/A'}</td>
                  <td className="px-5 py-3.5 text-center">
                    <span className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold ${
                      l.status === 'CLOSED' ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20' :
                      l.status === 'OVERDUE' ? 'bg-red-50 text-red-700 ring-1 ring-red-600/20' :
                      'bg-sky-50 text-sky-700 ring-1 ring-sky-600/20'
                    }`}>{l.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
