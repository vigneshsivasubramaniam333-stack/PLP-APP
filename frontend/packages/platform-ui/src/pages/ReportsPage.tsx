import { useState, useEffect } from 'react';
import { reportApi } from '@plp/shared';

const tabs = [
  { key: 'disbursement', label: 'Disbursement Summary', desc: 'Daily loan disbursement totals by product type' },
  { key: 'portfolio', label: 'Portfolio Summary', desc: 'Portfolio metrics and NPA analysis by program' },
  { key: 'overdue', label: 'Overdue / DPD', desc: 'Days past due aging analysis and overdue amounts' },
] as const;

type TabKey = typeof tabs[number]['key'];

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('disbursement');
  const [data, setData] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    const fetcher = activeTab === 'disbursement'
      ? reportApi.disbursementSummary()
      : activeTab === 'portfolio'
        ? reportApi.portfolioSummary()
        : reportApi.overdueReport();

    fetcher
      .then((res) => setData(Array.isArray(res.data) ? res.data : res.data.data || []))
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, [activeTab]);

  const exportCsv = () => {
    const fetcher = activeTab === 'disbursement'
      ? reportApi.exportDisbursement()
      : activeTab === 'portfolio'
        ? reportApi.exportPortfolio()
        : reportApi.exportOverdue();

    fetcher.then((res) => {
      const blob = new Blob([res.data], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${activeTab}-report.csv`;
      a.click();
      URL.revokeObjectURL(url);
    }).catch(console.error);
  };

  const columns = data.length > 0 ? Object.keys(data[0]) : [];
  const currentTab = tabs.find(t => t.key === activeTab)!;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
          <p className="text-sm text-slate-500 mt-1">Generate and export lending reports</p>
        </div>
        <button onClick={exportCsv} disabled={data.length === 0}
          className="inline-flex items-center gap-2 bg-white border border-slate-200 text-slate-700 px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50 shadow-sm">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          Export CSV
        </button>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 bg-slate-100 p-1 rounded-lg mb-6 w-fit">
        {tabs.map((tab) => (
          <button key={tab.key} onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-all ${
              activeTab === tab.key
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Report Card */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200">
        <div className="px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-700">{currentTab.label}</h3>
          <p className="text-xs text-slate-400 mt-0.5">{currentTab.desc}</p>
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-pulse text-slate-400 text-sm">Generating report...</div>
          </div>
        ) : data.length === 0 ? (
          <div className="flex items-center justify-center h-48">
            <div className="text-center">
              <div className="text-slate-400 text-sm">No data available</div>
              <p className="text-xs text-slate-400 mt-1">Data will appear once loans are processed</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  {columns.map((col) => (
                    <th key={col} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      {col.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((row, i) => (
                  <tr key={i} className="hover:bg-slate-50/80">
                    {columns.map((col) => (
                      <td key={col} className="px-5 py-3 text-slate-700 whitespace-nowrap">
                        {typeof row[col] === 'number'
                          ? (row[col] as number).toLocaleString('en-IN', { maximumFractionDigits: 2 })
                          : String(row[col] ?? '—')}
                      </td>
                    ))}
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
