import { useState, useEffect, useCallback } from 'react';
import { reportApi } from '@plp/shared';

const tabs = [
  { key: 'disbursement', label: 'Disbursement', desc: 'Daily loan disbursement totals by product type' },
  { key: 'collection', label: 'Collection', desc: 'Daily collection / repayment totals by product type' },
  { key: 'portfolio', label: 'Portfolio', desc: 'Portfolio metrics and NPA analysis by program' },
  { key: 'utilization', label: 'Utilization', desc: 'Program limit utilization and available headroom' },
  { key: 'overdue', label: 'Overdue / DPD', desc: 'Days past due aging analysis and overdue amounts' },
  { key: 'npa', label: 'NPA', desc: '90+ DPD and written-off loan exposure' },
  { key: 'invoice', label: 'Invoice Pipeline', desc: 'Invoice discounting status pipeline counts' },
  { key: 'onboarding', label: 'Onboarding', desc: 'Anchor and borrower onboarding funnel by status' },
  { key: 'workbench', label: 'Workbench', desc: 'Maker–checker pending queues by entity and status' },
] as const;

type TabKey = typeof tabs[number]['key'];

const DATE_FILTER_TABS = new Set<TabKey>(['disbursement', 'collection']);

function fetchReport(tab: TabKey, fromDate?: string, toDate?: string) {
  switch (tab) {
    case 'disbursement':
      return reportApi.disbursementSummary(fromDate || undefined, toDate || undefined);
    case 'collection':
      return reportApi.collectionSummary(fromDate || undefined, toDate || undefined);
    case 'portfolio':
      return reportApi.portfolioSummary();
    case 'utilization':
      return reportApi.programUtilization();
    case 'overdue':
      return reportApi.overdueReport();
    case 'npa':
      return reportApi.npaReport();
    case 'invoice':
      return reportApi.invoicePipeline();
    case 'onboarding':
      return reportApi.onboardingFunnel();
    case 'workbench':
      return reportApi.workbenchPending();
  }
}

function exportReport(tab: TabKey, fromDate?: string, toDate?: string) {
  switch (tab) {
    case 'disbursement':
      return reportApi.exportDisbursement(fromDate || undefined, toDate || undefined);
    case 'collection':
      return reportApi.exportCollection(fromDate || undefined, toDate || undefined);
    case 'portfolio':
      return reportApi.exportPortfolio();
    case 'utilization':
      return reportApi.exportProgramUtilization();
    case 'overdue':
      return reportApi.exportOverdue();
    case 'npa':
      return reportApi.exportNpa();
    case 'invoice':
      return reportApi.exportInvoicePipeline();
    case 'onboarding':
      return reportApi.exportOnboardingFunnel();
    case 'workbench':
      return reportApi.exportWorkbenchPending();
  }
}

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('disbursement');
  const [data, setData] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [appliedFrom, setAppliedFrom] = useState('');
  const [appliedTo, setAppliedTo] = useState('');

  const loadReport = useCallback(() => {
    setLoading(true);
    setError('');
    const from = DATE_FILTER_TABS.has(activeTab) ? appliedFrom : undefined;
    const to = DATE_FILTER_TABS.has(activeTab) ? appliedTo : undefined;
    fetchReport(activeTab, from, to)
      .then((res) => setData(Array.isArray(res.data?.data) ? res.data.data : []))
      .catch((err: unknown) => {
        setData([]);
        setError(err instanceof Error ? err.message : 'Failed to load report data');
      })
      .finally(() => setLoading(false));
  }, [activeTab, appliedFrom, appliedTo]);

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  const applyDateFilters = () => {
    setAppliedFrom(fromDate);
    setAppliedTo(toDate);
  };

  const clearDateFilters = () => {
    setFromDate('');
    setToDate('');
    setAppliedFrom('');
    setAppliedTo('');
  };

  const exportCsv = () => {
    const from = DATE_FILTER_TABS.has(activeTab) ? appliedFrom : undefined;
    const to = DATE_FILTER_TABS.has(activeTab) ? appliedTo : undefined;
    exportReport(activeTab, from, to)
      .then((res) => {
        const blob = new Blob([res.data], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${activeTab}-report.csv`;
        a.click();
        URL.revokeObjectURL(url);
      })
      .catch(console.error);
  };

  const columns = data.length > 0 ? Object.keys(data[0]) : [];
  const currentTab = tabs.find(t => t.key === activeTab)!;
  const showDateFilters = DATE_FILTER_TABS.has(activeTab);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
          <p className="text-sm text-slate-500 mt-1">Generate and export lending reports</p>
        </div>
        <button onClick={exportCsv} disabled={loading}
          className="inline-flex items-center gap-2 bg-white border border-slate-200 text-slate-700 px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50 shadow-sm">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          Export CSV
        </button>
      </div>

      {/* Tab Navigation */}
      <div className="flex flex-wrap gap-1 bg-slate-100 p-1 rounded-lg mb-6 w-fit max-w-full">
        {tabs.map((tab) => (
          <button key={tab.key} onClick={() => setActiveTab(tab.key)}
            className={`px-3 py-2 rounded-md text-sm font-medium transition-all whitespace-nowrap ${
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
        <div className="px-5 py-4 border-b border-slate-100 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-sm font-semibold text-slate-700">{currentTab.label}</h3>
            <p className="text-xs text-slate-400 mt-0.5">{currentTab.desc}</p>
          </div>
          {showDateFilters && (
            <div className="flex flex-wrap items-center gap-2">
              <label className="text-xs text-slate-500">
                From
                <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
                  className="ml-1.5 border border-slate-200 rounded-md px-2 py-1.5 text-sm text-slate-700" />
              </label>
              <label className="text-xs text-slate-500">
                To
                <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
                  className="ml-1.5 border border-slate-200 rounded-md px-2 py-1.5 text-sm text-slate-700" />
              </label>
              <button type="button" onClick={applyDateFilters}
                className="px-3 py-1.5 rounded-md text-xs font-medium bg-slate-800 text-white hover:bg-slate-700">
                Apply
              </button>
              <button type="button" onClick={clearDateFilters}
                className="px-3 py-1.5 rounded-md text-xs font-medium text-slate-600 hover:bg-slate-50 border border-slate-200">
                Clear
              </button>
            </div>
          )}
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-pulse text-slate-400 text-sm">Generating report...</div>
          </div>
        ) : error ? (
          <div className="flex items-center justify-center h-48 px-6">
            <div className="text-center">
              <div className="text-red-600 text-sm">{error}</div>
              <p className="text-xs text-slate-400 mt-1">Ensure report-service can reach lending-service with lender headers.</p>
            </div>
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
