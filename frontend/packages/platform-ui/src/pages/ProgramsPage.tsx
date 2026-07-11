import { useEffect, useState, type ReactNode } from 'react';
import { programApi, getStoredAuthUser, lenderLoanCapabilities, BtPageHeader, BtButton, BtBadge, BtCard, useAuth } from '@plp/shared';
import type { Program, ProgramEligibilityConfig, ProgramOperationalParameters } from '@plp/shared';
import { ClearDemoDataButton } from '../components/ClearDemoDataButton';
import {
  ProgramApprovalActions,
  ProgramApprovalToolbar,
  useProgramApprovalConfig,
} from '../components/ProgramApprovalActions';

const inputCls = 'bt-input w-full';
const labelCls = 'bt-label';
const formGridCls = 'grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4 items-start';
const sectionTitleCls = 'col-span-full text-xs font-semibold text-slate-500 uppercase tracking-wide pt-3 border-t border-slate-200 mt-2 mb-1 first:mt-0 first:pt-0 first:border-t-0';
const checkboxGridCls = 'col-span-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-2 py-1';
const modalShellCls = 'bt-modal !max-w-3xl w-full max-h-[min(92vh,880px)] flex flex-col overflow-hidden';
const modalFormCls = 'flex min-h-0 flex-1 flex-col';
const modalBodyScrollCls = 'bt-modal-body flex-1 min-h-0 overflow-y-auto overscroll-contain';

function ProgramFormModal({
  title,
  onClose,
  onSubmit,
  submitting,
  submitLabel,
  submittingLabel,
  error,
  children,
}: {
  title: string;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  submitting: boolean;
  submitLabel: string;
  submittingLabel: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="bt-modal-overlay">
      <div className={modalShellCls} role="dialog" aria-modal="true" aria-labelledby="program-modal-title">
        <div className="bt-modal-header shrink-0">
          <h2 id="program-modal-title" className="bt-modal-title">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="text-[var(--bt-gray-400)] hover:text-[var(--bt-gray-600)]"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <form onSubmit={onSubmit} className={modalFormCls}>
          <div className={modalBodyScrollCls}>
            {error ? (
              <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{error}</div>
            ) : null}
            {children}
          </div>
          <div className="bt-modal-footer shrink-0 border-t border-slate-100 bg-white">
            <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary">
              Cancel
            </button>
            <BtButton type="submit" disabled={submitting}>
              {submitting ? submittingLabel : submitLabel}
            </BtButton>
          </div>
        </form>
      </div>
    </div>
  );
}

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
  gapBetweenPreviousInvoiceDays: string;
  autoPullOption: boolean;
  gapBetweenSanctionAndDisbursementDays: string;
  intFreeCreditPeriod: boolean;
  intFreePeriodDays: string;
  autoPaymentBorrower: boolean;
  autoAcceptInvoices: boolean;
  sanctionType: string;
  partialDiscount: boolean;
  invoiceDelete: boolean;
  lmsEntryIn: string;
  encoreProductCode: string;
};

function parseBoolFlag(raw: unknown): boolean {
  return raw === true || raw === 'true' || raw === 'YES';
}

function buildParametersPayload(values: OperationalFormSlice): ProgramOperationalParameters {
  return {
    enablePaymentForBorrower: values.enablePaymentForBorrower,
    autoDiscounting: values.autoDiscounting,
    discountingDay: parseInt(values.discountingDay, 10) || 0,
    gapBetweenDiscountingDays: parseInt(values.gapBetweenDiscountingDays, 10) || 0,
    gapBetweenPreviousInvoiceDays: parseInt(values.gapBetweenPreviousInvoiceDays, 10) || 0,
    autoPullOption: values.autoPullOption,
    gapBetweenSanctionAndDisbursementDays: parseInt(values.gapBetweenSanctionAndDisbursementDays, 10) || 0,
    intFreeCreditPeriod: values.intFreeCreditPeriod,
    intFreePeriodDays: parseInt(values.intFreePeriodDays, 10) || 0,
    autoPaymentBorrower: values.autoPaymentBorrower,
    autoAcceptInvoices: values.autoAcceptInvoices,
    sanctionType: values.sanctionType,
    partialDiscount: values.partialDiscount,
    invoiceDelete: values.invoiceDelete,
  };
}

const defaultOperationalSlice = (): OperationalFormSlice => ({
  enablePaymentForBorrower: false,
  autoDiscounting: false,
  discountingDay: '0',
  gapBetweenDiscountingDays: '0',
  gapBetweenPreviousInvoiceDays: '0',
  autoPullOption: false,
  gapBetweenSanctionAndDisbursementDays: '0',
  intFreeCreditPeriod: false,
  intFreePeriodDays: '0',
  autoPaymentBorrower: false,
  autoAcceptInvoices: false,
  sanctionType: 'MANUAL',
  partialDiscount: false,
  invoiceDelete: false,
  lmsEntryIn: 'NO',
  encoreProductCode: '',
});

function OperationalParamsFields({
  values,
  onChange,
  showAutomationSection = true,
}: {
  values: OperationalFormSlice;
  onChange: (patch: Partial<OperationalFormSlice>) => void;
  showAutomationSection?: boolean;
}) {
  return (
    <>
      {showAutomationSection ? (
        <>
          <p className={sectionTitleCls}>Automation</p>
          <div className={checkboxGridCls}>
            <ProgramCheckbox
              label="Auto discounting"
              checked={values.autoDiscounting}
              onChange={(autoDiscounting) => onChange({ autoDiscounting })}
            />
            <ProgramCheckbox
              label="Auto accept invoices"
              checked={values.autoAcceptInvoices}
              onChange={(autoAcceptInvoices) => onChange({ autoAcceptInvoices })}
            />
            <ProgramCheckbox
              label="Auto pull option"
              checked={values.autoPullOption}
              onChange={(autoPullOption) => onChange({ autoPullOption })}
            />
            <ProgramCheckbox
              label="Auto payment (borrower)"
              checked={values.autoPaymentBorrower}
              onChange={(autoPaymentBorrower) => onChange({ autoPaymentBorrower })}
            />
            <ProgramCheckbox
              label="Partial discount"
              checked={values.partialDiscount}
              onChange={(partialDiscount) => onChange({ partialDiscount })}
            />
            <ProgramCheckbox
              label="Invoice delete allowed"
              checked={values.invoiceDelete}
              onChange={(invoiceDelete) => onChange({ invoiceDelete })}
            />
          </div>
        </>
      ) : null}

      <p className={sectionTitleCls}>Operational parameters</p>
      <div className={checkboxGridCls}>
        <ProgramCheckbox
          label="Enable payment for borrower"
          checked={values.enablePaymentForBorrower}
          onChange={(enablePaymentForBorrower) => onChange({ enablePaymentForBorrower })}
        />
        <ProgramCheckbox
          label="Interest-free credit period"
          checked={values.intFreeCreditPeriod}
          onChange={(intFreeCreditPeriod) => onChange({ intFreeCreditPeriod })}
        />
      </div>
      <ProgramField label="Sanction type">
        <select
          value={values.sanctionType}
          onChange={(e) => onChange({ sanctionType: e.target.value })}
          className={inputCls}
        >
          <option value="MANUAL">Manual</option>
          <option value="AUTO">Auto</option>
        </select>
      </ProgramField>
      <ProgramField label="Discounting day (of month)" hint="0–28 when auto discounting is enabled">
        <input
          type="number"
          min={0}
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
      <ProgramField label="Gap b/w previous invoice (days)">
        <input
          type="number"
          min={0}
          value={values.gapBetweenPreviousInvoiceDays}
          onChange={(e) => onChange({ gapBetweenPreviousInvoiceDays: e.target.value })}
          className={inputCls}
        />
      </ProgramField>
      <ProgramField label="Gap b/w sanction & disbursement (days)">
        <input
          type="number"
          min={0}
          value={values.gapBetweenSanctionAndDisbursementDays}
          onChange={(e) => onChange({ gapBetweenSanctionAndDisbursementDays: e.target.value })}
          className={inputCls}
        />
      </ProgramField>
      <ProgramField label="Interest-free period (days)" hint="Required > 0 when interest-free credit period is enabled">
        <input
          type="number"
          min={0}
          value={values.intFreePeriodDays}
          onChange={(e) => onChange({ intFreePeriodDays: e.target.value })}
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
        <ProgramField label="LMS loan product (Encore code)" span={2}>
          <input
            value={values.encoreProductCode}
            onChange={(e) => onChange({ encoreProductCode: e.target.value })}
            className={inputCls}
            placeholder="e.g. ID_INV_001"
          />
        </ProgramField>
      ) : null}
    </>
  );
}

function EligibilityFields({
  maxInvoiceAgeDays,
  minInvoiceAmount,
  minDaysToDueDate,
  dependencyVintagePercent,
  anchorRelationshipVintageMonths,
  onChange,
}: {
  maxInvoiceAgeDays: string;
  minInvoiceAmount: string;
  minDaysToDueDate: string;
  dependencyVintagePercent: string;
  anchorRelationshipVintageMonths: string;
  onChange: (patch: {
    maxInvoiceAgeDays?: string;
    minInvoiceAmount?: string;
    minDaysToDueDate?: string;
    dependencyVintagePercent?: string;
    anchorRelationshipVintageMonths?: string;
  }) => void;
}) {
  return (
    <>
      <p className={sectionTitleCls}>Eligibility (optional)</p>
      <ProgramField
        label="Age of invoice (days)"
        hint="Credit period: invoice date to due date (default 90 for invoice discounting)"
      >
        <input
          type="number"
          step="1"
          min={1}
          value={maxInvoiceAgeDays}
          onChange={(e) => onChange({ maxInvoiceAgeDays: e.target.value })}
          className={inputCls}
          placeholder="e.g. 90"
        />
      </ProgramField>
      <ProgramField label="Min invoice amount">
        <input
          type="number"
          step="0.01"
          min={0.01}
          value={minInvoiceAmount}
          onChange={(e) => onChange({ minInvoiceAmount: e.target.value })}
          className={inputCls}
          placeholder="Leave blank to skip"
        />
      </ProgramField>
      <ProgramField label="Min days to due date" span={2}>
        <input
          type="number"
          step="1"
          min={1}
          value={minDaysToDueDate}
          onChange={(e) => onChange({ minDaysToDueDate: e.target.value })}
          className={inputCls}
          placeholder="Leave blank to skip"
        />
      </ProgramField>
      <ProgramField
        label="Dependency vintage (%)"
        hint="Minimum borrower dependency on anchor required for eligibility"
      >
        <input
          type="number"
          step="0.01"
          min={0}
          value={dependencyVintagePercent}
          onChange={(e) => onChange({ dependencyVintagePercent: e.target.value })}
          className={inputCls}
          placeholder="e.g. 12.00"
        />
      </ProgramField>
      <ProgramField
        label="Anchor relationship vintage (months)"
        hint="Minimum months of anchor relationship required for eligibility"
      >
        <input
          type="number"
          step="1"
          min={1}
          value={anchorRelationshipVintageMonths}
          onChange={(e) => onChange({ anchorRelationshipVintageMonths: e.target.value })}
          className={inputCls}
          placeholder="e.g. 10"
        />
      </ProgramField>
      <p className="col-span-full text-[11px] text-slate-400 -mt-1">
        Only filled fields are sent; each must be greater than 0. Server merges into stored config.
      </p>
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
    enablePaymentForBorrower: parseBoolFlag(o.enablePaymentForBorrower),
    autoDiscounting: parseBoolFlag(o.autoDiscounting),
    discountingDay: typeof o.discountingDay === 'number' ? o.discountingDay : Number(o.discountingDay) || 0,
    gapBetweenDiscountingDays:
      typeof o.gapBetweenDiscountingDays === 'number'
        ? o.gapBetweenDiscountingDays
        : Number(o.gapBetweenDiscountingDays) || 0,
    gapBetweenPreviousInvoiceDays:
      typeof o.gapBetweenPreviousInvoiceDays === 'number'
        ? o.gapBetweenPreviousInvoiceDays
        : Number(o.gapBetweenPreviousInvoiceDays) || 0,
    autoPullOption: parseBoolFlag(o.autoPullOption),
    gapBetweenSanctionAndDisbursementDays:
      typeof o.gapBetweenSanctionAndDisbursementDays === 'number'
        ? o.gapBetweenSanctionAndDisbursementDays
        : Number(o.gapBetweenSanctionAndDisbursementDays) || 0,
    intFreeCreditPeriod: parseBoolFlag(o.intFreeCreditPeriod),
    intFreePeriodDays:
      typeof o.intFreePeriodDays === 'number' ? o.intFreePeriodDays : Number(o.intFreePeriodDays) || 0,
    autoPaymentBorrower: parseBoolFlag(o.autoPaymentBorrower),
    autoAcceptInvoices: parseBoolFlag(o.autoAcceptInvoices),
    sanctionType: typeof o.sanctionType === 'string' ? o.sanctionType : 'MANUAL',
    partialDiscount: parseBoolFlag(o.partialDiscount),
    invoiceDelete: parseBoolFlag(o.invoiceDelete),
  };
}

function fmtOpsSummary(p: Program): string {
  const o = parseOperationalParams(p);
  const parts: string[] = [];
  if (o.enablePaymentForBorrower) parts.push('Borrower pay');
  if (o.autoDiscounting) parts.push(`Auto disc${o.discountingDay != null ? ` d${o.discountingDay}` : ''}`);
  if (o.autoAcceptInvoices) parts.push('Auto accept');
  if (o.autoPullOption) parts.push('Auto pull');
  if (o.sanctionType === 'AUTO') parts.push('Auto sanction');
  if (o.partialDiscount) parts.push('Partial disc');
  if (o.invoiceDelete) parts.push('Del OK');
  if (o.intFreeCreditPeriod) parts.push(`Int-free ${o.intFreePeriodDays ?? 0}d`);
  if (p.lmsEntryIn === 'YES') parts.push(`LMS:${p.encoreProductCode || '—'}`);
  if (o.gapBetweenDiscountingDays != null && o.gapBetweenDiscountingDays > 0) {
    parts.push(`Gap ${o.gapBetweenDiscountingDays}d`);
  }
  return parts.length ? parts.join(' · ') : '—';
}

function fmtCfgSummary(c: ProgramEligibilityConfig): string {
  const parts: string[] = [];
  if (c.maxInvoiceAgeDays != null) parts.push(`credit≤${c.maxInvoiceAgeDays}d`);
  if (c.minInvoiceAmount != null) parts.push(`min ₹${Number(c.minInvoiceAmount).toLocaleString('en-IN')}`);
  if (c.minDaysToDueDate != null) parts.push(`due≥${c.minDaysToDueDate}d`);
  if (c.dependencyVintagePercent != null) parts.push(`dep≥${c.dependencyVintagePercent}%`);
  if (c.anchorRelationshipVintageMonths != null) parts.push(`anchor≥${c.anchorRelationshipVintageMonths}mo`);
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
  const [editForm, setEditForm] = useState({ ...defaultOperationalSlice(), name: '', description: '', marginPercent: '', maxInvoiceAgeDays: '', minInvoiceAmount: '', minDaysToDueDate: '', dependencyVintagePercent: '', anchorRelationshipVintageMonths: '' });

  const portalCaps = lenderLoanCapabilities(getStoredAuthUser()?.role);
  const { user } = useAuth();
  const isPlatformAdmin = user?.role === 'PLATFORM_ADMIN';
  const { config: approvalConfig, configModal, openConfig } = useProgramApprovalConfig();
  const sentBackCount = programs.filter((p) => p.status === 'SENT_BACK').length;

  const reloadPrograms = () => {
    programApi.list().then((res) => setPrograms(res.data.data || [])).catch(console.error);
  };

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
    maxInvoiceAgeDays: '',
    minInvoiceAmount: '',
    minDaysToDueDate: '',
    dependencyVintagePercent: '',
    anchorRelationshipVintageMonths: '',
    ...defaultOperationalSlice(),
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
      dependencyVintagePercent: cfg.dependencyVintagePercent != null ? String(cfg.dependencyVintagePercent) : '',
      anchorRelationshipVintageMonths:
        cfg.anchorRelationshipVintageMonths != null ? String(cfg.anchorRelationshipVintageMonths) : '',
      enablePaymentForBorrower: Boolean(ops.enablePaymentForBorrower),
      autoDiscounting: Boolean(ops.autoDiscounting),
      discountingDay: ops.discountingDay != null ? String(ops.discountingDay) : '0',
      gapBetweenDiscountingDays:
        ops.gapBetweenDiscountingDays != null ? String(ops.gapBetweenDiscountingDays) : '0',
      gapBetweenPreviousInvoiceDays:
        ops.gapBetweenPreviousInvoiceDays != null ? String(ops.gapBetweenPreviousInvoiceDays) : '0',
      autoPullOption: Boolean(ops.autoPullOption),
      gapBetweenSanctionAndDisbursementDays:
        ops.gapBetweenSanctionAndDisbursementDays != null ? String(ops.gapBetweenSanctionAndDisbursementDays) : '0',
      intFreeCreditPeriod: Boolean(ops.intFreeCreditPeriod),
      intFreePeriodDays: ops.intFreePeriodDays != null ? String(ops.intFreePeriodDays) : '0',
      autoPaymentBorrower: Boolean(ops.autoPaymentBorrower),
      autoAcceptInvoices: Boolean(ops.autoAcceptInvoices),
      sanctionType: ops.sanctionType ?? 'MANUAL',
      partialDiscount: Boolean(ops.partialDiscount),
      invoiceDelete: Boolean(ops.invoiceDelete),
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

  const parseNonNegativeOptional = (label: string, raw: string): number | undefined => {
    const t = raw.trim();
    if (!t) return undefined;
    const n = Number(t);
    if (Number.isNaN(n) || n < 0) throw new Error(`${label} must be 0 or greater`);
    return n;
  };

  const mergeVintageCfg = (cfg: Record<string, number>, dep: string, anchorMo: string) => {
    const depPct = parseNonNegativeOptional('Dependency vintage (%)', dep);
    const anchorMonths = parsePositiveOptional('Anchor relationship vintage (months)', anchorMo);
    if (depPct !== undefined) cfg.dependencyVintagePercent = depPct;
    if (anchorMonths !== undefined) cfg.anchorRelationshipVintageMonths = anchorMonths;
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
      mergeVintageCfg(cfg, editForm.dependencyVintagePercent, editForm.anchorRelationshipVintageMonths);
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
        parameters: buildParametersPayload(editForm),
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
      let cfgPayload: Record<string, number> | undefined;
      if (form.productType === 'INVOICE_DISCOUNTING') {
        const cfg: Record<string, number> = {};
        const maxAge = parsePositiveOptional('Age of invoice (days)', form.maxInvoiceAgeDays);
        const minAmt = parsePositiveOptional('Min invoice amount', form.minInvoiceAmount);
        const minDue = parsePositiveOptional('Min days to due date', form.minDaysToDueDate);
        if (maxAge !== undefined) cfg.maxInvoiceAgeDays = maxAge;
        if (minAmt !== undefined) cfg.minInvoiceAmount = minAmt;
        if (minDue !== undefined) cfg.minDaysToDueDate = minDue;
        mergeVintageCfg(cfg, form.dependencyVintagePercent, form.anchorRelationshipVintageMonths);
        if (Object.keys(cfg).length > 0) cfgPayload = cfg;
      }

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
        ...(cfgPayload ? { config: cfgPayload } : {}),
        parameters: buildParametersPayload(form),
        lmsEntryIn: form.lmsEntryIn,
        encoreProductCode: form.lmsEntryIn === 'YES' ? form.encoreProductCode.trim() : undefined,
        status: 'DRAFT',
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
        maxInvoiceAgeDays: '',
        minInvoiceAmount: '',
        minDaysToDueDate: '',
        dependencyVintagePercent: '',
        anchorRelationshipVintageMonths: '',
        ...defaultOperationalSlice(),
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
        description="Manage lending programs — L1/L2 approval from this listing"
        actions={
          <div className="flex flex-wrap items-center justify-end gap-2">
            {isPlatformAdmin ? <ClearDemoDataButton onCleared={reloadPrograms} /> : null}
            {portalCaps.canCreateProgramArtifacts ? (
              <BtButton onClick={() => setShowCreate(true)}>
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                </svg>
                Create Program
              </BtButton>
            ) : null}
          </div>
        }
      />

      <ProgramApprovalToolbar
        isPlatformAdmin={isPlatformAdmin}
        sentBackCount={sentBackCount}
        onOpenConfig={openConfig}
      />
      {configModal}

      {/* Create Program Modal */}
      {showCreate ? (
        <ProgramFormModal
          title="Create Program"
          onClose={() => setShowCreate(false)}
          onSubmit={handleCreate}
          submitting={creating}
          submitLabel="Create Program"
          submittingLabel="Creating..."
          error={error}
        >
          <p className="mb-5 text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
            Program codes are generated automatically. Anchor and facility limits belong on sub-programs under this umbrella.
          </p>
          <div className={formGridCls}>
            <p className={`${sectionTitleCls} first:mt-0 first:pt-0 first:border-t-0`}>Program details</p>
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
            {form.productType === 'INVOICE_DISCOUNTING' ? (
              <EligibilityFields
                maxInvoiceAgeDays={form.maxInvoiceAgeDays}
                minInvoiceAmount={form.minInvoiceAmount}
                minDaysToDueDate={form.minDaysToDueDate}
                dependencyVintagePercent={form.dependencyVintagePercent}
                anchorRelationshipVintageMonths={form.anchorRelationshipVintageMonths}
                onChange={(patch) => setForm({ ...form, ...patch })}
              />
            ) : null}
            <OperationalParamsFields values={form} onChange={(patch) => setForm({ ...form, ...patch })} />
          </div>
        </ProgramFormModal>
      ) : null}

      {editProgram ? (
        <ProgramFormModal
          title="Edit program"
          onClose={() => setEditProgram(null)}
          onSubmit={handleEditSave}
          submitting={editSaving}
          submitLabel="Save"
          submittingLabel="Saving..."
          error={editError}
        >
          <div className={formGridCls}>
            <p className={`${sectionTitleCls} first:mt-0 first:pt-0 first:border-t-0`}>Program details</p>
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
            <OperationalParamsFields values={editForm} onChange={(patch) => setEditForm({ ...editForm, ...patch })} />
            {editProgram.productType === 'INVOICE_DISCOUNTING' ? (
              <EligibilityFields
                maxInvoiceAgeDays={editForm.maxInvoiceAgeDays}
                minInvoiceAmount={editForm.minInvoiceAmount}
                minDaysToDueDate={editForm.minDaysToDueDate}
                dependencyVintagePercent={editForm.dependencyVintagePercent}
                anchorRelationshipVintageMonths={editForm.anchorRelationshipVintageMonths}
                onChange={(patch) => setEditForm({ ...editForm, ...patch })}
              />
            ) : null}
          </div>
        </ProgramFormModal>
      ) : null}

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
                  <BtBadge status={p.status}>
                    {p.status === 'PENDING_L2' ? 'Pending L2' : p.status === 'SENT_BACK' ? 'Sent back' : p.status}
                  </BtBadge>
                  {p.approvalRemarks ? (
                    <div className="text-[10px] text-amber-700 mt-1 max-w-[120px] mx-auto line-clamp-2" title={p.approvalRemarks}>
                      {p.approvalRemarks}
                    </div>
                  ) : null}
                </td>
                <td className="px-5 py-4 text-center">
                  <ProgramApprovalActions
                    program={p}
                    approvalConfig={approvalConfig}
                    onChanged={reload}
                    canEdit={portalCaps.canEditProgramConfig}
                    onEdit={() => openEdit(p)}
                  />
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
