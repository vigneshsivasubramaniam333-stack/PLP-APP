import { useState } from 'react';
import { reportApi } from '@plp/shared';

type ReportType = 'disbursement' | 'portfolio' | 'overdue';

const reports = [
  {
    type: 'disbursement' as ReportType,
    label: 'Disbursement Summary',
    desc: 'Daily disbursement totals by product type',
    color: 'bg-sky-50 text-sky-600',
  },
  {
    type: 'portfolio' as ReportType,
    label: 'Portfolio Summary',
    desc: 'Program-wise portfolio health with NPA metrics',
    color: 'bg-emerald-50 text-emerald-600',
  },
  {
    type: 'overdue' as ReportType,
    label: 'Overdue / DPD Report',
    desc: 'Overdue loans with DPD aging buckets',
    color: 'bg-red-50 text-red-600',
  },
];

export default function ReportsPage() {
  const [downloading, setDownloading] = useState(false);

  async function download(type: ReportType) {
    setDownloading(true);
    try {
      let res;
      if (type === 'disbursement') res = await reportApi.exportDisbursement();
      else if (type === 'portfolio') res = await reportApi.exportPortfolio();
      else res = await reportApi.exportOverdue();
      const blob = new Blob([res.data], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${type}_report.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) { console.error('Download failed', e); }
    setDownloading(false);
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
        <p className="text-sm text-slate-500 mt-1">Download reports in CSV format for your programs</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {reports.map(r => (
          <div key={r.type} className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className={`w-10 h-10 rounded-lg ${r.color} flex items-center justify-center mb-4`}>
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <h3 className="text-sm font-semibold text-slate-800 mb-1">{r.label}</h3>
            <p className="text-xs text-slate-500 mb-5">{r.desc}</p>
            <button onClick={() => download(r.type)} disabled={downloading}
              className="w-full py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50 flex items-center justify-center gap-2">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              {downloading ? 'Downloading...' : 'Download CSV'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
