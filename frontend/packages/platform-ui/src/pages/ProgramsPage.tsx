import { useEffect, useState, type ReactNode } from 'react';
import { programApi, getStoredAuthUser, lenderLoanCapabilities, BtPageHeader, BtButton, BtBadge, BtCard } from '@plp/shared';
import type { Program, ProgramEligibilityConfig, ProgramOperationalParameters } from '@plp/shared';

const inputCls = 'bt-input w-full';
const labelCls = 'bt-label';
const formGridCls = 'grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4 items-start';
const sectionTitleCls = 'col-span-full text-xs font-semibold text-slate-500 uppercase tracking-wide pt-2 border-t border-slate-200 mt-1 mb-1';
const checkboxRowCls = 'col-span-full flex flex-col sm:flex-row sm:flex-wrap gap-4 sm:gap-8 py-1';

function ProgramField({
  label,
  hint,
  span = 1,
  children,
}: {
  label: string;
  hint?: string;
  span?: 1 | 2;
  children: ReactNode;
}) {
  return (
    <div className={span === 2 ? 'md:col-span-2' : undefined}>
      <label className={labelCls}>{label}</label>
      {children}
      {hint ? <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{hint}</p> : null}
    </div>
  );
}

function ProgramCheckbox({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="inline-flex items-center gap-2.5 text-sm text-slate-700 cursor-pointer select-none min-h-[38px]">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="h-4 w-4 shrink-0 rounded border-slate-300"
      />
      <span className="leading-snug">{label}</span>
    </label>
  );
}

type OperationalFormSlice = {
  enablePaymentForBorrower: boolean;
  autoDiscounting: boolean;
  discountingDay: string;
  gapBetweenDiscountingDays: string;
  lmsEntryIn: string;
  encoreProductCode: string;
};

function OperationalParamsFields({
  values,
  onChange,
}: {
  values: OperationalFormSlice;
  onChange: (patch: Partial<OperationalFormSlice>) => void;
}) {
  return (
    <>
      <p className={sectionTitleCls}>Operational parameters</p>
      <div className={checkboxRowCls}>
        <ProgramCheckbox
          label="Enable payment for borrower"
          checked={values.enablePaymentForBorrower}
          onChange={(enablePaymentForBorrower) => onChange({ enablePaymentForBorrower })}
        />
        <ProgramCheckbox
          label="Auto discounting"
          checked={values.autoDiscounting}
          onChange={(autoDiscounting) => onChange({ autoDiscounting })}
        />
      </div>
      <ProgramField label="Discounting day (of month)">
        <input
          type="number"
          min={1}
          max={28}
          value={values.discountingDay}
          onChange={(e) => onChange({ discountingDay: e.target.value })}
          className={inputCls}
        />
      </ProgramField>
      <ProgramField label="Gap b/w discounting (days)">
        <input
          type="number"
          min={0}
          value={values.gapBetweenDiscountingDays}
          onChange={(e) => onChange({ gapBetweenDiscountingDays: e.target.value })}
          className={inputCls}
        />
      </ProgramField>
      <ProgramField label="Need LMS entry?">
        <select
          value={values.lmsEntryIn}
          onChange={(e) => onChange({ lmsEntryIn: e.target.value })}
          className={inputCls}
        >
          <option value="NO">No</option>
          <option value="YES">Yes</option>
        </select>
      </ProgramField>
      {values.lmsEntryIn === 'YES' ? (
        <ProgramField label="LMS loan product (Encore code)">
          <input
            value={values.encoreProductCode}
            onChange={(e) => onChange({ encoreProductCode: e.target.value })}
            className={inputCls}
            placeholder="e.g. ID_INV_001"
          />
        </ProgramField>
      ) : (
        <div className="hidden md:block" aria-hidden />
      )}
    </>
  );
}

function parseEligibleCfg(p: Program): ProgramEligibilityConfig {
  const c = p.config;
  return c && typeof c === 'object' ? (c as ProgramEligibilityConfig) : {};
}

function parseOperationalParams(p: Program): ProgramOperationalParameters {
  const raw = p.parameters;
  if (!raw || typeof raw !== 'object') return {};
  const o = raw as Record<string, unknown>;
  return {
    enablePaymentForBorrower: o.enablePaymentForBorrower === true || o.enablePaymentForBorrower === 'true',
    autoDiscounting: o.autoDiscounting === true || o.autoDiscounting === 'true',
    discountingDay: typeof o.discountingDay === 'number' ? o.discountingDay : Number(o.discountingDay) || undefined,
    gapBetweenDiscountingDays:
      typeof o.gapBetweenDiscountingDays === 'number'
        ? o.gapBetweenDiscountingDays
        : Number(o.gapBetweenDiscountingDays) || undefined,
  };
}

function fmtOpsSummary(p: Program): string {
  const o = parseOperationalParams(p);
  const parts: string[] = [];
  if (o.enablePaymentForBorrower) parts.push('Borrower pay');
  if (o.autoDiscounting) parts.push(`Auto disc${o.discountingDay != null ? ` d${o.discountingDay}` : ''}`);
  if (p.lmsEntryIn === 'YES') parts.push(`LMS:${p.encoreProductCode || '—'}`);
  if (o.gapBetweenDiscountingDays != null && o.gapBetweenDiscountingDays > 0) {
    parts.push(`Gap ${o.gapBetweenDiscountingDays}d`);
  }
  return parts.length ? parts.join(' · ') : '—';
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
    marginPercent: '',
    maxInvoiceAgeDays: '',
    minInvoiceAmount: '',
    minDaysToDueDate: '',
    enablePaymentForBorrower: false,
    autoDiscounting: false,
    discountingDay: '1',
    gapBetweenDiscountingDays: '0',
    lmsEntryIn: 'NO',
    encoreProductCode: '',
  });

  const portalCaps = lenderLoanCapabilities(getStoredAuthUser()?.role);

  const [form, setForm] = useState({
    programName: '',
    productType: 'PAY_DAY_LOAN',
    lenderId: '00000000-0000-0000-0000-000000000001',
    programLimit: '',
    maxBorrowerLimit: '',
    defaultInterestRate: '',
    marginPercent: '0',
    maxTenureDays: '30',
    maxConcurrentLoans: '1',
    gracePeriodDays: '3',
    coolingOffDays: '3',
    enablePaymentForBorrower: false,
    autoDiscounting: false,
    discountingDay: '1',
    gapBetweenDiscountingDays: '0',
    lmsEntryIn: 'NO',
    encoreProductCode: '',
  });

  useEffect(() => {
    programApi.list().then((res) => setPrograms(res.data.data || [])).catch(console.error).finally(() => setLoading(false));
  }, []);

  const reload = () => {
    programApi.list().then((res) => setPrograms(res.data.data || [])).catch(console.error);
  };

  const openEdit = (p: Program) => {
    setEditProgram(p);
    setEditError('');
    void programApi
      .get(p.id)
      .then((res) => {
        const full = (res.data?.data ?? p) as Program;
        setEditProgram(full);
        populateEditForm(full);
      })
      .catch(() => populateEditForm(p));
  };

  const populateEditForm = (p: Program) => {
    const cfg = parseEligibleCfg(p);
    const ops = parseOperationalParams(p);
    setEditForm({
      name: p.programName,
      description: p.description ?? '',
      marginPercent: p.marginPercent != null ? String(p.marginPercent) : '',
      maxInvoiceAgeDays: cfg.maxInvoiceAgeDays != null ? String(cfg.maxInvoiceAgeDays) : '',
      minInvoiceAmount: cfg.minInvoiceAmount != null ? String(cfg.minInvoiceAmount) : '',
      minDaysToDueDate: cfg.minDaysToDueDate != null ? String(cfg.minDaysToDueDate) : '',
      enablePaymentForBorrower: Boolean(ops.enablePaymentForBorrower),
      autoDiscounting: Boolean(ops.autoDiscounting),
      discountingDay: ops.discountingDay != null ? String(ops.discountingDay) : '1',
      gapBetweenDiscountingDays:
        ops.gapBetweenDiscountingDays != null ? String(ops.gapBetweenDiscountingDays) : '0',
      lmsEntryIn: p.lmsEntryIn === 'YES' ? 'YES' : 'NO',
      encoreProductCode: p.encoreProductCode ?? '',
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

      const marginVal = editForm.marginPercent.trim();
      const marginNum = marginVal !== '' ? parseFloat(marginVal) : undefined;
      if (marginNum !== undefined && (Number.isNaN(marginNum) || marginNum < 0)) {
        throw new Error('Margin % must be 0 or greater');
      }

      await programApi.update(editProgram.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim(),
        ...(marginNum !== undefined ? { marginPercent: marginNum } : {}),
        ...(cfgPayload ? { config: cfgPayload } : {}),
        parameters: {
          enablePaymentForBorrower: editForm.enablePaymentForBorrower,
          autoDiscounting: editForm.autoDiscounting,
          discountingDay: parseInt(editForm.discountingDay, 10) || 1,
          gapBetweenDiscountingDays: parseInt(editForm.gapBetweenDiscountingDays, 10) || 0,
        },
        lmsEntryIn: editForm.lmsEntryIn,
        encoreProductCode: editForm.lmsEntryIn === 'YES' ? editForm.encoreProductCode.trim() : '',
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
        marginPercent: form.marginPercent ? parseFloat(form.marginPercent) : 0,
        maxTenureDays: parseInt(form.maxTenureDays, 10),
        maxConcurrentLoans: parseInt(form.maxConcurrentLoans, 10),
        gracePeriodDays: parseInt(form.gracePeriodDays, 10),
        coolingOffDays: parseInt(form.coolingOffDays, 10),
        parameters: {
          enablePaymentForBorrower: form.enablePaymentForBorrower,
          autoDiscounting: form.autoDiscounting,
          discountingDay: parseInt(form.discountingDay, 10) || 1,
          gapBetweenDiscountingDays: parseInt(form.gapBetweenDiscountingDays, 10) || 0,
        },
        lmsEntryIn: form.lmsEntryIn,
        encoreProductCode: form.lmsEntryIn === 'YES' ? form.encoreProductCode.trim() : undefined,
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
        marginPercent: '0',
        maxTenureDays: '30',
        maxConcurrentLoans: '1',
        gracePeriodDays: '3',
        coolingOffDays: '3',
        enablePaymentForBorrower: false,
        autoDiscounting: false,
        discountingDay: '1',
        gapBetweenDiscountingDays: '0',
        lmsEntryIn: 'NO',
        encoreProductCode: '',
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
      <BtPageHeader
        title="Programs"
        description="Manage lending programs and configurations"
        actions={
          portalCaps.canCreateProgramArtifacts ? (
            <BtButton onClick={() => setShowCreate(true)}>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              Create Program
            </BtButton>
          ) : undefined
        }
      />

      {/* Create Program Modal */}
      {showCreate && (
        <div className="bt-modal-overlay">
          <div className="bt-modal max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="bt-modal-header">
              <h2 className="bt-modal-title">Create Program</h2>
              <button type="button" onClick={() => setShowCreate(false)} className="text-[var(--bt-gray-400)] hover:text-[var(--bt-gray-600)]">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="bt-modal-body">
                {error && (
                  <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{error}</div>
                )}
                <p className="mb-5 text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
                  Program codes are generated automatically. Anchor and facility limits belong on sub-programs under this umbrella.
                </p>
                <div className={formGridCls}>
                  <ProgramField label="Program Name *" span={2}>
                    <input
                      value={form.programName}
                      onChange={(e) => setForm({ ...form, programName: e.target.value })}
                      className={inputCls}
                      placeholder="e.g., ACME Pay Day Loan"
                      required
                    />
                  </ProgramField>
                  <ProgramField label="Product Type *">
                    <select
                      value={form.productType}
                      onChange={(e) => setForm({ ...form, productType: e.target.value })}
                      className={inputCls}
                    >
                      <option value="PAY_DAY_LOAN">Pay Day Loan</option>
                      <option value="INVOICE_DISCOUNTING">Invoice Discounting</option>
                    </select>
                  </ProgramField>
                  <ProgramField label="Umbrella program limit (INR) *">
                    <input
                      type="number"
                      step="0.01"
                      value={form.programLimit}
                      onChange={(e) => setForm({ ...form, programLimit: e.target.value })}
                      className={inputCls}
                      placeholder="e.g., 10000000"
                      required
                    />
                  </ProgramField>
                  <ProgramField label="Max Borrower Limit (INR) *">
                    <input
                      type="number"
                      step="0.01"
                      value={form.maxBorrowerLimit}
                      onChange={(e) => setForm({ ...form, maxBorrowerLimit: e.target.value })}
                      className={inputCls}
                      placeholder="e.g., 100000"
                      required
                    />
                  </ProgramField>
                  <ProgramField label="Interest Rate (% p.a.) *">
                    <input
                      type="number"
                      step="0.01"
                      value={form.defaultInterestRate}
                      onChange={(e) => setForm({ ...form, defaultInterestRate: e.target.value })}
                      className={inputCls}
                      placeholder="e.g., 18"
                      required
                    />
                  </ProgramField>
                  <ProgramField label="Margin (%)" hint="0 = eligible equals net amount">
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.marginPercent}
                      onChange={(e) => setForm({ ...form, marginPercent: e.target.value })}
                      className={inputCls}
                      placeholder="0"
                    />
                  </ProgramField>
                  <ProgramField label="Max Tenure (days)">
                    <input
                      type="number"
                      value={form.maxTenureDays}
                      onChange={(e) => setForm({ ...form, maxTenureDays: e.target.value })}
                      className={inputCls}
                    />
                  </ProgramField>
                  <ProgramField label="Max Concurrent Loans">
                    <input
                      type="number"
                      value={form.maxConcurrentLoans}
                      onChange={(e) => setForm({ ...form, maxConcurrentLoans: e.target.value })}
                      className={inputCls}
                    />
                  </ProgramField>
                  <OperationalParamsFields
                    values={form}
                    onChange={(patch) => setForm({ ...form, ...patch })}
                  />
                </div>
              </div>
              <div className="bt-modal-footer">
                <button type="button" onClick={() => setShowCreate(false)} className="bt-btn bt-btn-secondary">
                  Cancel
                </button>
                <BtButton type="submit" disabled={creating}>
                  {creating ? 'Creating...' : 'Create Program'}
                </BtButton>
              </div>
            </form>
          </div>
        </div>
      )}

      {editProgram && (
        <div className="bt-modal-overlay">
          <div className="bt-modal max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="bt-modal-header">
              <h2 className="bt-modal-title">Edit program</h2>
              <button
                type="button"
                onClick={() => setEditProgram(null)}
                className="text-[var(--bt-gray-400)] hover:text-[var(--bt-gray-600)]"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <form onSubmit={handleEditSave}>
              <div className="bt-modal-body">
                {editError && (
                  <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{editError}</div>
                )}
                <div className={formGridCls}>
                  <ProgramField label="Name" span={2}>
                    <input
                      value={editForm.name}
                      onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                      className={inputCls}
                      required
                    />
                  </ProgramField>
                  <ProgramField label="Description" span={2}>
                    <textarea
                      value={editForm.description}
                      onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                      className={`${inputCls} min-h-[88px] resize-y`}
                      placeholder="Optional"
                    />
                  </ProgramField>
                  <ProgramField
                    label="Margin (%)"
                    span={2}
                    hint="Deducted from invoice net amount to calculate eligible amount. Leave blank or set to 0 for full eligibility."
                  >
                    <input
                      type="number"
                      step="0.01"
                      min={0}
                      value={editForm.marginPercent}
                      onChange={(e) => setEditForm({ ...editForm, marginPercent: e.target.value })}
                      className={inputCls}
                      placeholder="0 = no margin (eligible = net amount)"
                    />
                  </ProgramField>
                  <OperationalParamsFields
                    values={editForm}
                    onChange={(patch) => setEditForm({ ...editForm, ...patch })}
                  />
                  <p className={sectionTitleCls}>Eligibility parameters (optional)</p>
                  <ProgramField label="Max invoice age (days)">
                    <input
                      type="number"
                      step="1"
                      min={1}
                      value={editForm.maxInvoiceAgeDays}
                      onChange={(e) => setEditForm({ ...editForm, maxInvoiceAgeDays: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </ProgramField>
                  <ProgramField label="Min invoice amount">
                    <input
                      type="number"
                      step="0.01"
                      min={0.01}
                      value={editForm.minInvoiceAmount}
                      onChange={(e) => setEditForm({ ...editForm, minInvoiceAmount: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </ProgramField>
                  <ProgramField label="Min days to due date" span={2}>
                    <input
                      type="number"
                      step="1"
                      min={1}
                      value={editForm.minDaysToDueDate}
                      onChange={(e) => setEditForm({ ...editForm, minDaysToDueDate: e.target.value })}
                      className={inputCls}
                      placeholder="Leave blank to skip"
                    />
                  </ProgramField>
                  <p className="col-span-full text-[11px] text-slate-400 -mt-1">
                    Only filled fields are sent; each must be greater than 0. Server merges into stored config.
                  </p>
                </div>
              </div>
              <div className="bt-modal-footer">
                <button
                  type="button"
                  onClick={() => setEditProgram(null)}
                  className="bt-btn bt-btn-secondary"
                >
                  Cancel
                </button>
                <BtButton type="submit" disabled={editSaving}>
                  {editSaving ? 'Saving...' : 'Save'}
                </BtButton>
              </div>
            </form>
          </div>
        </div>
      )}

      <BtCard className="overflow-hidden p-0">
        <table className="bt-table w-full">
          <thead>
            <tr>
              <th>Program</th>
              <th>Product Type</th>
              <th className="text-right">Program Limit</th>
              <th className="text-right">Consumed</th>
              <th className="text-right">Available</th>
              <th className="text-right">Interest Rate</th>
              <th className="text-right">Margin %</th>
              <th className="max-w-[200px]">Eligibility config</th>
              <th className="max-w-[180px]">Operations / LMS</th>
              <th className="text-center">Status</th>
              <th className="text-center">Actions</th>
            </tr>
          </thead>
          <tbody>
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
                <td className="px-5 py-4 text-right text-slate-600">
                  {p.marginPercent != null ? `${p.marginPercent}%` : '0%'}
                </td>
                <td className="px-5 py-4 text-xs text-slate-600 max-w-[220px]" title={fmtCfgSummary(parseEligibleCfg(p))}>
                  <span className="line-clamp-2">{fmtCfgSummary(parseEligibleCfg(p))}</span>
                </td>
                <td className="px-5 py-4 text-xs text-slate-600 max-w-[200px]" title={fmtOpsSummary(p)}>
                  <span className="line-clamp-2">{fmtOpsSummary(p)}</span>
                </td>
                <td className="px-5 py-4 text-center">
                  <BtBadge status={p.status}>{p.status}</BtBadge>
                </td>
                <td className="px-5 py-4 text-center">
                  {portalCaps.canEditProgramConfig ? (
                    <button
                      type="button"
                      onClick={() => openEdit(p)}
                      className="bt-btn bt-btn-secondary bt-btn-sm"
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
                <td colSpan={11} className="px-5 py-12 text-center">
                  <div className="text-slate-400 text-sm">No programs created yet</div>
                  <p className="text-xs text-slate-400 mt-1">Click "Create Program" to get started</p>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </BtCard>
    </div>
  );
}
