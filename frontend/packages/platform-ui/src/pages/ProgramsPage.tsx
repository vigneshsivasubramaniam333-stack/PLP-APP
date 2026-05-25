import { useEffect, useState } from 'react';
import { programApi, getStoredAuthUser, lenderLoanCapabilities } from '@plp/shared';
import type { Program, ProgramEligibilityConfig } from '@plp/shared';

const inputCls = 'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

function parseEligibleCfg(p: Program): ProgramEligibilityConfig {
  const c = p.config;
  return c && typeof c === 'object' ? (c as ProgramEligibilityConfig) : {};
}

function fmtCfgSummary(c: ProgramEligibilityConfig): string {
  const parts: string[] = [];
  if (c.maxInvoiceAgeDays != null) parts.push(`age≤${c.maxInvoiceAgeDays}d`);
  if (c.minInvoiceAmount != null) parts.push(`min ₹${Number(c.minInvoiceAmount).toLocaleString('en-IN')}`);
  if (c.minDaysToDueDate != null) parts.push(`due≥${c.minDaysToDueDate}d`);
  return parts.length ? parts.join(' · ') : '—';
}

export default function ProgramsPage() {
  const [programs, setPrograms] = useState<Program[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [editProgram, setEditProgram] = useState<Program | null>(null);
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState('');
  const [editForm, setEditForm] = useState({
    name: '',
    description: '',
    maxInvoiceAgeDays: '',
    minInvoiceAmount: '',
    minDaysToDueDate: '',
  });

  const portalCaps = lenderLoanCapabilities(getStoredAuthUser()?.role);

  const [form, setForm] = useState({
    programName: '',
    productType: 'PAY_DAY_LOAN',
    lenderId: '00000000-0000-0000-0000-000000000001',
    programLimit: '',
    maxBorrowerLimit: '',
    defaultInterestRate: '',
    maxTenureDays: '30',
    maxConcurrentLoans: '1',
    gracePeriodDays: '3',
    coolingOffDays: '3',
  });

  useEffect(() => {
    programApi.list().then((res) => setPrograms(res.data.data || [])).catch(console.error).finally(() => setLoading(false));
  }, []);

  const reload = () => {
    programApi.list().then((res) => setPrograms(res.data.data || [])).catch(console.error);
  };

  const openEdit = (p: Program) => {
    const cfg = parseEligibleCfg(p);
    setEditProgram(p);
    setEditError('');
    setEditForm({
      name: p.programName,
      description: p.description ?? '',
      maxInvoiceAgeDays: cfg.maxInvoiceAgeDays != null ? String(cfg.maxInvoiceAgeDays) : '',
      minInvoiceAmount: cfg.minInvoiceAmount != null ? String(cfg.minInvoiceAmount) : '',
      minDaysToDueDate: cfg.minDaysToDueDate != null ? String(cfg.minDaysToDueDate) : '',
    });
  };

  const parsePositiveOptional = (label: string, raw: string): number | undefined => {
    const t = raw.trim();
    if (!t) return undefined;
    const n = Number(t);
    if (Number.isNaN(n) || n <= 0) throw new Error(`${label} must be greater than 0`);
    return n;
  };

  const handleEditSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editProgram) return;
    setEditSaving(true);
    setEditError('');
    try {
      if (!editForm.name.trim()) {
        throw new Error('Program name is required');
      }
      let cfgPayload: Record<string, number> | undefined;
      const cfg: Record<string, number> = {};
      const maxAge = parsePositiveOptional('Max invoice age (days)', editForm.maxInvoiceAgeDays);
      const minAmt = parsePositiveOptional('Min invoice amount', editForm.minInvoiceAmount);
      const minDue = parsePositiveOptional('Min days to due date', editForm.minDaysToDueDate);
      if (maxAge !== undefined) cfg.maxInvoiceAgeDays = maxAge;
      if (minAmt !== undefined) cfg.minInvoiceAmount = minAmt;
      if (minDue !== undefined) cfg.minDaysToDueDate = minDue;
      if (Object.keys(cfg).length > 0) cfgPayload = cfg;

      await programApi.update(editProgram.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim(),
        ...(cfgPayload ? { config: cfgPayload } : {}),
      });
      setEditProgram(null);
      reload();
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : typeof err === 'object' && err !== null && 'response' in err
            ? String((err as { response?: { data?: { message?: string } } }).response?.data?.message)
            : 'Failed to save';
      setEditError(msg || 'Failed to save');
    } finally {
      setEditSaving(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError('');
    try {
      await programApi.create({
        programName: form.programName,
        productType: form.productType,
        lenderId: form.lenderId,
        programLimit: parseFloat(form.programLimit),
        maxBorrowerLimit: parseFloat(form.maxBorrowerLimit),
        defaultInterestRate: parseFloat(form.defaultInterestRate),
        maxTenureDays: parseInt(form.maxTenureDays, 10),
        maxConcurrentLoans: parseInt(form.maxConcurrentLoans, 10),
        gracePeriodDays: parseInt(form.gracePeriodDays, 10),
        coolingOffDays: parseInt(form.coolingOffDays, 10),
        status: 'ACTIVE',
      });
      setShowCreate(false);
      setForm({
        programName: '',
        productType: 'PAY_DAY_LOAN',
        lenderId: '00000000-0000-0000-0000-000000000001',
        programLimit: '',
        maxBorrowerLimit: '',
        defaultInterestRate: '',
        maxTenureDays: '30',
        maxConcurrentLoans: '1',
        gracePeriodDays: '3',
        coolingOffDays: '3',
      });
      reload();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create program';
      setError(msg);
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading programs...</div>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Programs</h1>
          <p className="text-sm text-slate-500 mt-1">Manage lending programs and configurations</p>
        </div>
        {portalCaps.canCreateProgramArtifacts && (
          <button
            onClick={() => setShowCreate(true)}
            className="inline-flex items-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 shadow-sm"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            Create Program
          </button>
        )}
      </div>

      {/* Create Program Modal */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">Create Program</h2>
              <button onClick={() => setShowCreate(false)} className="text-slate-400 hover:text-slate-600">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <form onSubmit={handleCreate} className="p-6">
              {error && (
                <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{error}</div>
              )}
              <p className="mb-4 text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
                Program codes are generated automatically. Anchor and facility limits belong on sub-programs under this umbrella.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="md:col-span-2">
                  <label className={labelCls}>Program Name *</label>
                  <input value={form.programName} onChange={(e) => setForm({...form, programName: e.target.value})}
                    className={inputCls} placeholder="e.g., ACME Pay Day Loan" required />
                </div>
                <div>
                  <label className={labelCls}>Product Type *</label>
                  <select value={form.productType} onChange={(e) => setForm({...form, productType: e.target.value})}
                    className={inputCls}>
                    <option value="PAY_DAY_LOAN">Pay Day Loan</option>
                    <option value="INVOICE_DISCOUNTING">Invoice Discounting</option>
                  </select>
                </div>
                <div>
                  <label className={labelCls}>Umbrella program limit (INR) *</label>
                  <input type="number" step="0.01" value={form.programLimit}
                    onChange={(e) => setForm({...form, programLimit: e.target.value})}
                    className={inputCls} placeholder="e.g., 10000000" required />
                </div>
                <div>
                  <label className={labelCls}>Max Borrower Limit (INR) *</label>
                  <input type="number" step="0.01" value={form.maxBorrowerLimit}
                    onChange={(e) => setForm({...form, maxBorrowerLimit: e.target.value})}
                    className={inputCls} placeholder="e.g., 100000" required />
                </div>
                <div>
                  <label className={labelCls}>Interest Rate (% p.a.) *</label>
                  <input type="number" step="0.01" value={form.defaultInterestRate}
                    onChange={(e) => setForm({...form, defaultInterestRate: e.target.value})}
                    className={inputCls} placeholder="e.g., 18" required />
                </div>
                <div>
                  <label className={labelCls}>Max Tenure (days)</label>
                  <input type="number" value={form.maxTenureDays}
                    onChange={(e) => setForm({...form, maxTenureDays: e.target.value})}
                    className={inputCls} />
                </div>
                <div>
                  <label className={labelCls}>Max Concurrent Loans</label>
                  <input type="number" value={form.maxConcurrentLoans}
                    onChange={(e) => setForm({...form, maxConcurrentLoans: e.target.value})}
                    className={inputCls} />
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
                <button type="button" onClick={() => setShowCreate(false)}
                  className="px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50">
                  Cancel
                </button>
                <button type="submit" disabled={creating}
                  className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50">
                  {creating ? 'Creating...' : 'Create Program'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editProgram && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">Edit program</h2>
              <button
                type="button"
                onClick={() => setEditProgram(null)}
                className="text-slate-400 hover:text-slate-600"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <form onSubmit={handleEditSave} className="p-6 space-y-4">
              {editError && (
                <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{editError}</div>
              )}
              <div>
                <label className={labelCls}>Name</label>
                <input
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  className={inputCls}
                  required
                />
              </div>
              <div>
                <label className={labelCls}>Description</label>
                <textarea
                  value={editForm.description}
                  onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                  className={`${inputCls} min-h-[88px] resize-y`}
                  placeholder="Optional"
                />
              </div>
              <div className="pt-2 border-t border-slate-100">
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">
                  Eligibility parameters (optional)
                </p>
                <div className="space-y-3">
                  <div>
                    <label className={labelCls}>Max invoice age (days)</label>
                    <input
                      type="number"
                      step="1"
                      min={1}
                      value={editForm.maxInvoiceAgeDays}
                      onChange={(e) => setEditForm({ ...editForm, maxInvoiceAgeDays: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </div>
                  <div>
                    <label className={labelCls}>Min invoice amount</label>
                    <input
                      type="number"
                      step="0.01"
                      min={0.01}
                      value={editForm.minInvoiceAmount}
                      onChange={(e) => setEditForm({ ...editForm, minInvoiceAmount: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </div>
                  <div>
                    <label className={labelCls}>Min days to due date</label>
                    <input
                      type="number"
                      step="1"
                      min={1}
                      value={editForm.minDaysToDueDate}
                      onChange={(e) => setEditForm({ ...editForm, minDaysToDueDate: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </div>
                </div>
                <p className="text-[11px] text-slate-400 mt-2">
                  Only filled fields are sent; each must be greater than 0. Server merges into stored config.
                </p>
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setEditProgram(null)}
                  className="px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={editSaving}
                  className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50"
                >
                  {editSaving ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200">
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Program</th>
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Product Type</th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Program Limit</th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Consumed</th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Available</th>
              <th className="px-5 py-3.5 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Interest Rate</th>
              <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider max-w-[200px]">
                Eligibility config
              </th>
              <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
              <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {programs.map((p) => (
              <tr key={p.id} className="hover:bg-slate-50/80">
                <td className="px-5 py-4">
                  <div className="font-medium text-slate-800">{p.programName}</div>
                  <div className="text-xs text-slate-400 font-mono mt-0.5">{p.programCode}</div>
                  {p.description ? (
                    <div className="text-xs text-slate-500 mt-1 line-clamp-2">{p.description}</div>
                  ) : null}
                </td>
                <td className="px-5 py-4">
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium ${
                    p.productType === 'PAY_DAY_LOAN'
                      ? 'bg-blue-50 text-blue-700'
                      : 'bg-purple-50 text-purple-700'
                  }`}>
                    {p.productType === 'PAY_DAY_LOAN' ? 'Pay Day Loan' : 'Invoice Discounting'}
                  </span>
                </td>
                <td className="px-5 py-4 text-right font-medium text-slate-700">
                  {new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(p.programLimit)}
                </td>
                <td className="px-5 py-4 text-right text-slate-600 tabular-nums">
                  {new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
                    p.utilizedLimit ?? 0,
                  )}
                </td>
                <td className="px-5 py-4 text-right text-slate-700 font-medium tabular-nums">
                  {new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
                    p.availableLimit ?? Math.max(0, p.programLimit - (p.utilizedLimit ?? 0)),
                  )}
                </td>
                <td className="px-5 py-4 text-right text-slate-600">{p.defaultInterestRate}% p.a.</td>
                <td className="px-5 py-4 text-xs text-slate-600 max-w-[220px]" title={fmtCfgSummary(parseEligibleCfg(p))}>
                  <span className="line-clamp-2">{fmtCfgSummary(parseEligibleCfg(p))}</span>
                </td>
                <td className="px-5 py-4 text-center">
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold ${
                    p.status === 'ACTIVE'
                      ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                      : 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20'
                  }`}>{p.status}</span>
                </td>
                <td className="px-5 py-4 text-center">
                  {portalCaps.canEditProgramConfig ? (
                    <button
                      type="button"
                      onClick={() => openEdit(p)}
                      className="text-xs font-semibold text-blue-600 hover:text-blue-800 bg-blue-50 px-3 py-1.5 rounded-lg"
                    >
                      Edit
                    </button>
                  ) : (
                    <span className="text-xs text-slate-400">—</span>
                  )}
                </td>
              </tr>
            ))}
            {programs.length === 0 && (
              <tr>
                <td colSpan={9} className="px-5 py-12 text-center">
                  <div className="text-slate-400 text-sm">No programs created yet</div>
                  <p className="text-xs text-slate-400 mt-1">Click "Create Program" to get started</p>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
