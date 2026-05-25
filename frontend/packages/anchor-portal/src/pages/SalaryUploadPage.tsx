import { useState, useEffect, useRef, useMemo } from 'react';
import { borrowerApi, programApi, salaryApi, portalApi, subProgramApi, useAuth } from '@plp/shared';
import type { Program, EmployeeSalaryData, Borrower, SubProgram } from '@plp/shared';

const NO_LINKED_BORROWERS =
  'No linked borrowers for this sub-program. Ask your lender to enroll employees on this Pay Loan sub-program.';

const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

function anchorIdFromUser(linkedType: string | null | undefined, linkedId: string | null | undefined): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'ANCHOR') return '';
  return (linkedId ?? '').trim();
}

function isPayLoanSubProgram(sp: SubProgram, programs: Program[]): boolean {
  if (sp.flowType !== 'PAY_LOAN' && sp.flowType !== 'PAY_DAY_LOAN') return false;
  const parent = programs.find((p) => p.id === sp.programId);
  return parent?.productType === 'PAY_DAY_LOAN';
}

export default function SalaryUploadPage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [payPeriod, setPayPeriod] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [uploadResult, setUploadResult] = useState<{ rows: number; error?: string } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [salaryRecords, setSalaryRecords] = useState<EmployeeSalaryData[]>([]);
  const [tab, setTab] = useState<'upload' | 'manual' | 'view'>('upload');
  const fileRef = useRef<HTMLInputElement>(null);

  const [manual, setManual] = useState({
    borrowerId: '',
    employeeCode: '',
    externalReferenceNumber: '',
    grossSalary: '',
    netSalary: '',
    daysWorked: '0',
    totalWorkingDays: '30',
    deductions: '0',
  });
  const [manualMsg, setManualMsg] = useState('');
  const [borrowersPick, setBorrowersPick] = useState<Borrower[]>([]);

  const pdlSubPrograms = useMemo(() => {
    return subPrograms.filter((sp) => isPayLoanSubProgram(sp, programs) && sp.status === 'ACTIVE');
  }, [subPrograms, programs]);

  /** Umbrella program id required by salary upload APIs. */
  const umbrellaProgramId = useMemo(() => {
    const sp = pdlSubPrograms.find((s) => s.id === selectedSubProgramId);
    return sp?.programId ?? '';
  }, [pdlSubPrograms, selectedSubProgramId]);

  useEffect(() => {
    Promise.all([
      programApi.list().then((r) => setPrograms(r.data.data || [])),
      anchorId ? subProgramApi.list().then((r) => setSubPrograms(r.data.data || [])) : Promise.resolve(),
    ]).catch(console.error);
  }, [anchorId]);

  useEffect(() => {
    setManual((m) => ({ ...m, borrowerId: '' }));
  }, [selectedSubProgramId]);

  useEffect(() => {
    if (!anchorId || !selectedSubProgramId || !umbrellaProgramId) {
      setBorrowersPick([]);
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const memRes = await subProgramApi.listBorrowers(selectedSubProgramId);
        if (cancelled) return;
        const memberRows = (memRes.data?.data ?? []) as { borrowerId: string }[];
        const ids = new Set(memberRows.map((row) => row.borrowerId));
        if (ids.size === 0) {
          if (!cancelled) setBorrowersPick([]);
          return;
        }
        const br = await borrowerApi.list({ anchorId });
        if (cancelled) return;
        const all = (br.data?.data as Borrower[] | undefined) ?? [];
        setBorrowersPick(all.filter((b) => ids.has(b.id)));
      } catch {
        if (!cancelled) setBorrowersPick([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [anchorId, selectedSubProgramId, umbrellaProgramId]);

  const handleUpload = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file || !anchorId || !umbrellaProgramId || !payPeriod) return;
    setUploading(true);
    setUploadResult(null);
    try {
      const res = await portalApi.anchorSalaryUpload(anchorId, umbrellaProgramId, payPeriod, file);
      setUploadResult({ rows: res.data.data.rowsProcessed });
      loadSalaryData();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Upload failed';
      setUploadResult({ rows: 0, error: message });
    } finally {
      setUploading(false);
    }
  };

  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setManualMsg('');
    if (!anchorId || !umbrellaProgramId) return;
    try {
      await salaryApi.create({
        borrowerId: manual.borrowerId,
        anchorId,
        programId: umbrellaProgramId,
        employeeCode: manual.employeeCode,
        payPeriod,
        ...(manual.externalReferenceNumber.trim()
          ? { externalReferenceNumber: manual.externalReferenceNumber.trim() }
          : {}),
        grossSalary: parseFloat(manual.grossSalary),
        netSalary: parseFloat(manual.netSalary),
        daysWorked: parseInt(manual.daysWorked, 10),
        totalWorkingDays: parseInt(manual.totalWorkingDays, 10),
        deductions: parseFloat(manual.deductions || '0'),
        source: 'MANUAL',
      });
      setManualMsg('Salary entry saved successfully');
      setManual({
        borrowerId: '',
        employeeCode: '',
        externalReferenceNumber: '',
        grossSalary: '',
        netSalary: '',
        daysWorked: '0',
        totalWorkingDays: '30',
        deductions: '0',
      });
      loadSalaryData();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Save failed';
      setManualMsg('Error: ' + message);
    }
  };

  const loadSalaryData = () => {
    if (!anchorId) return;
    portalApi
      .anchorSalary(anchorId)
      .then((r) => {
        const payload = r.data ?? r;
        const slips = Array.isArray(payload) ? payload : (payload as { data?: unknown })?.data ?? [];
        setSalaryRecords(Array.isArray(slips) ? slips : []);
      })
      .catch(console.error);
  };

  useEffect(() => {
    if (anchorId) loadSalaryData();
  }, [anchorId]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Salary Upload &amp; Management</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Salary Upload &amp; Management</h1>
        <p className="text-sm text-slate-500 mt-1">
          Scoped to your anchor · Pay Loan sub-programs only · CSV applies to borrowers on the selected sub-program
        </p>
      </div>

      {/* Context selectors */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className={labelCls}>Sub-program (Pay Loan / PDL)</label>
            <select
              value={selectedSubProgramId}
              onChange={(e) => {
                setSelectedSubProgramId(e.target.value);
              }}
              className={inputCls}
            >
              <option value="">Select Pay Loan sub-program</option>
              {pdlSubPrograms.map((sp) => {
                const parent = programs.find((p) => p.id === sp.programId);
                return (
                  <option key={sp.id} value={sp.id}>
                    {sp.code} — {sp.name}
                    {parent ? ` (${parent.programCode})` : ''}
                  </option>
                );
              })}
            </select>
            {pdlSubPrograms.length === 0 && (
              <p className="text-xs text-amber-700 mt-1.5">
                No active Pay Loan sub-programs for your anchor. Ask your lender to provision one.
              </p>
            )}
          </div>
          <div>
            <label className={labelCls}>Pay period</label>
            <input type="month" value={payPeriod} onChange={(e) => setPayPeriod(e.target.value)} className={inputCls} />
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-5 border-b border-slate-200">
        {(['upload', 'manual', 'view'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 -mb-px ${
              tab === t ? 'border-emerald-600 text-emerald-700' : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t === 'upload' ? 'CSV upload' : t === 'manual' ? 'Manual entry' : 'View data'}
          </button>
        ))}
      </div>

      {/* CSV Upload Tab */}
      {tab === 'upload' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-slate-700 mb-3">Upload salary CSV</h3>
          <p className="text-xs text-slate-500 mb-4">
            Columns:{' '}
            <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">
              employee_code, borrower_code, gross_salary, net_salary, days_worked, total_working_days, deductions
            </code>
          </p>
          <div className="flex items-center gap-4">
            <input ref={fileRef} type="file" accept=".csv" className="text-sm text-slate-600" />
            <button
              onClick={() => void handleUpload()}
              disabled={uploading || !umbrellaProgramId}
              className="px-5 py-2.5 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 disabled:opacity-50"
            >
              {uploading ? 'Uploading...' : 'Upload CSV'}
            </button>
          </div>
          {uploadResult && (
            <div
              className={`mt-4 p-4 rounded-lg text-sm flex items-center gap-2 ${
                uploadResult.error
                  ? 'bg-red-50 text-red-700 border border-red-200'
                  : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              }`}
            >
              <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                {uploadResult.error ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                )}
              </svg>
              {uploadResult.error || `Successfully processed ${uploadResult.rows} records`}
            </div>
          )}
        </div>
      )}

      {/* Manual Entry Tab */}
      {tab === 'manual' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-slate-700 mb-4">Manual salary entry</h3>
          <form onSubmit={handleManualSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="md:col-span-2">
              <label className={labelCls}>Borrower (employee) *</label>
              <select
                value={manual.borrowerId}
                onChange={(e) => setManual({ ...manual, borrowerId: e.target.value })}
                className={inputCls}
                required
                disabled={!selectedSubProgramId}
              >
                <option value="">Select borrower</option>
                {borrowersPick.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} — {b.borrowerCode}
                  </option>
                ))}
              </select>
              {!selectedSubProgramId ? (
                <p className="text-xs text-slate-500 mt-1">Choose a Pay Loan sub-program first.</p>
              ) : borrowersPick.length === 0 ? (
                <p className="text-xs text-amber-700 mt-1.5">{NO_LINKED_BORROWERS}</p>
              ) : null}
            </div>
            <div>
              <label className={labelCls}>Employee code</label>
              <input
                value={manual.employeeCode}
                onChange={(e) => setManual({ ...manual, employeeCode: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div className="md:col-span-2">
              <label className={labelCls}>External reference (optional)</label>
              <input
                value={manual.externalReferenceNumber}
                onChange={(e) => setManual({ ...manual, externalReferenceNumber: e.target.value })}
                className={inputCls}
                placeholder="Employer payroll reference"
              />
            </div>
            <div>
              <label className={labelCls}>Gross salary</label>
              <input
                type="number"
                step="0.01"
                value={manual.grossSalary}
                onChange={(e) => setManual({ ...manual, grossSalary: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Net salary</label>
              <input
                type="number"
                step="0.01"
                value={manual.netSalary}
                onChange={(e) => setManual({ ...manual, netSalary: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Days worked</label>
              <input
                type="number"
                value={manual.daysWorked}
                onChange={(e) => setManual({ ...manual, daysWorked: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Total working days</label>
              <input
                type="number"
                value={manual.totalWorkingDays}
                onChange={(e) => setManual({ ...manual, totalWorkingDays: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Deductions</label>
              <input
                type="number"
                step="0.01"
                value={manual.deductions}
                onChange={(e) => setManual({ ...manual, deductions: e.target.value })}
                className={inputCls}
              />
            </div>
            <div className="flex items-end">
              <button
                type="submit"
                disabled={!umbrellaProgramId || borrowersPick.length === 0 || !manual.borrowerId}
                className="px-5 py-2.5 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 disabled:opacity-50"
              >
                Save entry
              </button>
            </div>
          </form>
          {manualMsg && (
            <div
              className={`mt-4 p-4 rounded-lg text-sm ${
                manualMsg.startsWith('Error')
                  ? 'bg-red-50 text-red-700 border border-red-200'
                  : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              }`}
            >
              {manualMsg}
            </div>
          )}
        </div>
      )}

      {/* View Data Tab */}
      {tab === 'view' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center">
            <h3 className="text-sm font-semibold text-slate-700">Salary slips (all periods)</h3>
            <span className="text-xs text-slate-400">{salaryRecords.length} records</span>
          </div>
          {salaryRecords.length > 0 ? (
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Slip #</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">External ref</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Period</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Employee</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Gross</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Net</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Accumulated</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Eligible</th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Days</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {salaryRecords.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50/80">
                    <td className="px-5 py-3 font-mono text-xs text-slate-800">{r.salarySlipNumber ?? '—'}</td>
                    <td className="px-5 py-3 text-xs text-slate-600">{r.externalReferenceNumber ?? '—'}</td>
                    <td className="px-5 py-3 text-xs text-slate-700">{r.payPeriod}</td>
                    <td className="px-5 py-3 text-xs font-medium text-slate-700">{r.slipStatus ?? '—'}</td>
                    <td className="px-5 py-3">
                      <div className="font-mono text-xs text-slate-600">{r.employeeCode}</div>
                    </td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(r.grossSalary)}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(r.netSalary)}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(r.accumulatedSalary)}</td>
                    <td className="px-5 py-3 text-right font-semibold text-emerald-600">{formatCurrency(r.eligibleAmount)}</td>
                    <td className="px-5 py-3 text-center text-slate-600">
                      {r.daysWorked}/{r.totalWorkingDays}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="px-5 py-12 text-center text-slate-400 text-sm">No salary slips uploaded yet</div>
          )}
        </div>
      )}
    </div>
  );
}
