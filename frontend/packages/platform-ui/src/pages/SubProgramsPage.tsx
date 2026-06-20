import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  programApi,
  anchorApi,
  subProgramApi,
  borrowerApi,
  extractApiErrorMessage,
  getStoredAuthUser,
  lenderLoanCapabilities,
  BtButton,
  BtBadge,
  buildBorrowerTermsRows,
  buildBorrowerTermsRowsFromMap,
  buildSubProgramConfigurationRows,
} from '@plp/shared';
import type { Program, Anchor, SubProgram, SubProgramBorrower, Borrower } from '@plp/shared';

const inputCls = 'bt-input w-full';
const labelCls = 'bt-label';
const formGridCls = 'grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4 items-start';
const sectionTitleCls =
  'col-span-full text-xs font-semibold text-slate-500 uppercase tracking-wide pt-3 border-t border-slate-200 mt-2 mb-1 first:mt-0 first:pt-0 first:border-t-0';
const modalShellCls = 'bt-modal !max-w-3xl w-full max-h-[min(92vh,880px)] flex flex-col overflow-hidden my-auto';
const modalShellXlCls = 'bt-modal !max-w-5xl w-full max-h-[min(92vh,920px)] flex flex-col overflow-hidden my-auto';
const modalFormCls = 'flex min-h-0 flex-1 flex-col';
const modalBodyScrollCls = 'flex-1 min-h-0 overflow-y-auto overscroll-contain px-6 py-6';

type DetailSubview = 'overview' | 'edit' | 'add-borrower' | 'edit-borrower';

type BorrowerTermsForm = {
  borrowerLimit: string;
  interestRate: string;
  discountMarginPercent: string;
  creditPeriodDays: string;
  discountHold: string;
  paymentMethod: string;
  overdueInterestRate: string;
};

const DEFAULT_BORROWER_TERMS: Omit<BorrowerTermsForm, 'borrowerLimit'> = {
  interestRate: '',
  discountMarginPercent: '',
  creditPeriodDays: '',
  discountHold: 'NO',
  paymentMethod: 'SMART_COLLECT',
  overdueInterestRate: '0',
};

function emptyAddBorrowerForm() {
  return { borrowerId: '', borrowerLimit: '', ...DEFAULT_BORROWER_TERMS };
}

function borrowerTermsFormFromMembership(row: SubProgramBorrower): BorrowerTermsForm {
  return {
    borrowerLimit: row.borrowerLimit != null ? String(row.borrowerLimit) : '',
    interestRate: row.interestRate != null ? String(row.interestRate) : '',
    discountMarginPercent: row.discountMarginPercent != null ? String(row.discountMarginPercent) : '',
    creditPeriodDays: row.creditPeriodDays != null ? String(row.creditPeriodDays) : '',
    discountHold: row.discountHold ?? 'NO',
    paymentMethod: row.paymentMethod ?? 'SMART_COLLECT',
    overdueInterestRate: row.overdueInterestRate != null ? String(row.overdueInterestRate) : '0',
  };
}

function appendBorrowerTermsToPayload(
  payload: Record<string, unknown>,
  form: BorrowerTermsForm,
  opts: { includeLimit?: boolean; onCreate?: boolean },
) {
  if (opts.includeLimit && form.borrowerLimit.trim()) {
    payload.borrowerLimit = parseFloat(form.borrowerLimit);
  }
  if (form.interestRate.trim()) payload.interestRate = parseFloat(form.interestRate);
  if (form.discountMarginPercent.trim()) payload.discountMarginPercent = parseFloat(form.discountMarginPercent);
  if (form.creditPeriodDays.trim()) payload.creditPeriodDays = parseInt(form.creditPeriodDays, 10);
  if (opts.onCreate || form.discountHold) payload.discountHold = form.discountHold;
  if (opts.onCreate || form.paymentMethod) payload.paymentMethod = form.paymentMethod;
  if (form.overdueInterestRate.trim()) payload.overdueInterestRate = parseFloat(form.overdueInterestRate);
}

function rolesForInvoiceDiscountingFlow(flowType: string): { anchorRole: string; borrowerRole: string } {
  if (flowType === 'SALES_BILL_DISCOUNTING') {
    return { anchorRole: 'BUYER', borrowerRole: 'SELLER' };
  }
  return { anchorRole: 'SELLER', borrowerRole: 'BUYER' };
}

function formatInrAmount(n: number | null | undefined): string {
  if (n == null || Number.isNaN(Number(n))) return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
}

function subProgramFlowLabel(flowType: string | undefined | null): string {
  const f = (flowType ?? '').trim();
  switch (f) {
    case 'PURCHASE_BILL_DISCOUNTING':
      return 'Purchase Bill Discounting';
    case 'SALES_BILL_DISCOUNTING':
      return 'Sales Bill Discounting';
    case 'PAY_LOAN':
    case 'PAY_DAY_LOAN':
      return 'Pay Loan';
    default:
      return f || '—';
  }
}

function subProgramStatusBadgeClass(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'bg-emerald-50 text-emerald-800 ring-1 ring-emerald-600/20';
    case 'DRAFT':
      return 'bg-amber-50 text-amber-800 ring-1 ring-amber-600/20';
    case 'INACTIVE':
      return 'bg-slate-100 text-slate-600 ring-1 ring-slate-500/15';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

function borrowerHasTermOverrides(row: SubProgramBorrower): boolean {
  return (
    row.interestRate != null ||
    row.discountMarginPercent != null ||
    row.creditPeriodDays != null ||
    (row.discountHold != null && row.discountHold !== '') ||
    (row.paymentMethod != null && row.paymentMethod !== '') ||
    (row.overdueInterestRate != null && Number(row.overdueInterestRate) !== 0)
  );
}

function FormField({
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

function LimitStat({ label, value, tone }: { label: string; value: string; tone?: 'default' | 'amber' | 'green' }) {
  const toneCls =
    tone === 'amber' ? 'text-amber-700' : tone === 'green' ? 'text-emerald-700' : 'text-slate-800';
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3.5 min-w-[120px]">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-lg font-semibold tabular-nums ${toneCls}`}>{value}</div>
    </div>
  );
}

function TermsGrid({ title, rows, empty }: { title: string; rows: { label: string; value: string }[]; empty?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-4">
      <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-3">{title}</h5>
      {rows.length === 0 ? (
        <p className="text-sm text-slate-400">{empty ?? '—'}</p>
      ) : (
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
          {rows.map((row) => (
            <div key={row.label}>
              <dt className="text-[11px] font-medium uppercase tracking-wide text-slate-500">{row.label}</dt>
              <dd className="mt-0.5 text-sm font-medium text-slate-800">{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

function CreateFormModal({
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
      <div className={modalShellCls} role="dialog" aria-modal="true">
        <div className="bt-modal-header shrink-0">
          <h2 className="bt-modal-title">{title}</h2>
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

function DetailModalShell({
  title,
  subtitle,
  onClose,
  onBack,
  footer,
  children,
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
  onBack?: () => void;
  footer?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="bt-modal-overlay">
      <div className={modalShellXlCls} role="dialog" aria-modal="true">
        <div className="bt-modal-header shrink-0 gap-4">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            {onBack ? (
              <button
                type="button"
                onClick={onBack}
                className="shrink-0 inline-flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                </svg>
                Back
              </button>
            ) : null}
            <div className="min-w-0">
              <h2 className="bt-modal-title truncate">{title}</h2>
              {subtitle ? <p className="text-xs text-slate-500 truncate mt-0.5">{subtitle}</p> : null}
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 text-[var(--bt-gray-400)] hover:text-[var(--bt-gray-600)]"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden">{children}</div>
        {footer ? <div className="bt-modal-footer shrink-0 border-t border-slate-100 bg-white">{footer}</div> : null}
      </div>
    </div>
  );
}

function BorrowerMembershipCard({
  subProgramId,
  row,
  displayName,
  displayCode,
  canEdit,
  onEdit,
}: {
  subProgramId: string;
  row: SubProgramBorrower;
  displayName: string;
  displayCode: string | null;
  canEdit: boolean;
  onEdit: () => void;
}) {
  const [pricingOpen, setPricingOpen] = useState(false);
  const [effectiveRows, setEffectiveRows] = useState<{ label: string; value: string }[]>([]);
  const [effectiveLoading, setEffectiveLoading] = useState(false);
  const [effectiveError, setEffectiveError] = useState('');
  const overrideRows = buildBorrowerTermsRows(row);

  useEffect(() => {
    if (!pricingOpen) return;
    let cancelled = false;
    setEffectiveLoading(true);
    setEffectiveError('');
    void subProgramApi
      .getEffectiveBorrowerTerms(subProgramId, row.borrowerId)
      .then((res) => {
        if (cancelled) return;
        const data = (res.data as { data?: Record<string, unknown> }).data;
        setEffectiveRows(buildBorrowerTermsRowsFromMap(data ?? null));
      })
      .catch((err: unknown) => {
        if (!cancelled) setEffectiveError(extractApiErrorMessage(err, 'Could not load effective pricing'));
      })
      .finally(() => {
        if (!cancelled) setEffectiveLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [pricingOpen, subProgramId, row.borrowerId]);

  return (
    <article className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      <div className="p-5">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className="text-base font-semibold text-slate-800">{displayName}</h4>
          <BtBadge status={row.status}>{row.status}</BtBadge>
        </div>
        {displayCode ? <p className="mt-0.5 font-mono text-xs text-slate-400">{displayCode}</p> : null}

        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <LimitStat label="Sanctioned" value={formatInrAmount(row.borrowerLimit)} />
          <LimitStat label="Utilized" value={formatInrAmount(row.utilizedLimit)} tone="amber" />
          <LimitStat label="Available" value={formatInrAmount(row.availableLimit)} tone="green" />
        </div>

        <div className="mt-5 flex flex-col sm:flex-row sm:items-center sm:justify-end gap-2 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={() => setPricingOpen((v) => !v)}
            className="bt-btn bt-btn-secondary w-full sm:w-36 justify-center !h-10 !px-4"
          >
            {pricingOpen ? 'Hide pricing' : 'View pricing'}
          </button>
          {canEdit ? (
            <button
              type="button"
              onClick={onEdit}
              className="bt-btn bt-btn-primary w-full sm:w-36 justify-center !h-10 !px-4"
            >
              Edit borrower
            </button>
          ) : null}
        </div>
      </div>
      {pricingOpen ? (
        <div className="border-t border-slate-100 bg-slate-50/40 px-5 py-5 space-y-4">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <TermsGrid
              title="Configured overrides"
              rows={overrideRows}
              empty="No overrides — inherits sub-program defaults"
            />
            <TermsGrid
              title="Effective pricing"
              rows={effectiveLoading ? [] : effectiveRows}
              empty={effectiveLoading ? 'Loading…' : effectiveError || 'No pricing data'}
            />
          </div>
          {effectiveError && !effectiveLoading ? (
            <p className="text-sm text-red-600">{effectiveError}</p>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}

function BorrowerTermsFields({
  form,
  onChange,
  limitRequired,
}: {
  form: BorrowerTermsForm;
  onChange: (next: BorrowerTermsForm) => void;
  limitRequired?: boolean;
}) {
  return (
    <>
      <div className={sectionTitleCls}>Borrower terms</div>
      <p className="col-span-full text-xs text-slate-500 -mt-2">
        Leave pricing fields blank to inherit from the sub-program. Effective values apply at runtime.
      </p>
      {limitRequired ? (
        <div className="col-span-full md:col-span-1">
          <label className={labelCls}>Borrower limit *</label>
          <input
            required
            type="number"
            step="0.01"
            min={0}
            value={form.borrowerLimit}
            onChange={(e) => onChange({ ...form, borrowerLimit: e.target.value })}
            className={inputCls}
          />
          <p className="text-[11px] text-slate-400 mt-1">Utilized starts at 0; available equals limit until used.</p>
        </div>
      ) : (
        <div className="col-span-full md:col-span-1">
          <label className={labelCls}>Borrower limit</label>
          <input
            type="number"
            step="0.01"
            min={0}
            value={form.borrowerLimit}
            onChange={(e) => onChange({ ...form, borrowerLimit: e.target.value })}
            className={inputCls}
          />
        </div>
      )}
      <div>
        <label className={labelCls}>Interest rate (%)</label>
        <input
          type="number"
          step="0.01"
          min={0}
          value={form.interestRate}
          onChange={(e) => onChange({ ...form, interestRate: e.target.value })}
          className={inputCls}
          placeholder="Inherit"
        />
      </div>
      <div>
        <label className={labelCls}>Discount margin (%)</label>
        <input
          type="number"
          step="0.01"
          min={0}
          value={form.discountMarginPercent}
          onChange={(e) => onChange({ ...form, discountMarginPercent: e.target.value })}
          className={inputCls}
          placeholder="Inherit"
        />
      </div>
      <div>
        <label className={labelCls}>Credit period (days)</label>
        <input
          type="number"
          min={0}
          value={form.creditPeriodDays}
          onChange={(e) => onChange({ ...form, creditPeriodDays: e.target.value })}
          className={inputCls}
          placeholder="Inherit"
        />
      </div>
      <div>
        <label className={labelCls}>Overdue interest (%)</label>
        <input
          type="number"
          step="0.01"
          min={0}
          value={form.overdueInterestRate}
          onChange={(e) => onChange({ ...form, overdueInterestRate: e.target.value })}
          className={inputCls}
        />
      </div>
      <div>
        <label className={labelCls}>Discount hold</label>
        <select
          value={form.discountHold}
          onChange={(e) => onChange({ ...form, discountHold: e.target.value })}
          className={inputCls}
        >
          <option value="NO">No</option>
          <option value="YES">Yes</option>
        </select>
      </div>
      <div>
        <label className={labelCls}>Payment method</label>
        <select
          value={form.paymentMethod}
          onChange={(e) => onChange({ ...form, paymentMethod: e.target.value })}
          className={inputCls}
        >
          <option value="SMART_COLLECT">Smart Collect</option>
        </select>
      </div>
    </>
  );
}


export default function SubProgramsPage() {
  const [programs, setPrograms] = useState<Program[]>([]);
  const [anchors, setAnchors] = useState<Anchor[]>([]);
  const [filterProgramId, setFilterProgramId] = useState('');
  const [filterAnchorId, setFilterAnchorId] = useState('');
  const [allSubPrograms, setAllSubPrograms] = useState<SubProgram[]>([]);
  const [loading, setLoading] = useState(true);
  const [listRefreshing, setListRefreshing] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<SubProgram | null>(null);
  const [detailSubview, setDetailSubview] = useState<DetailSubview>('overview');
  const [detailBorrowers, setDetailBorrowers] = useState<SubProgramBorrower[]>([]);
  const [detailBorrowersLoading, setDetailBorrowersLoading] = useState(false);
  const [programBorrowersPick, setProgramBorrowersPick] = useState<Borrower[]>([]);
  const [addBorrowerSubmitting, setAddBorrowerSubmitting] = useState(false);
  const [addBorrowerError, setAddBorrowerError] = useState('');
  const [borrowerIdManual, setBorrowerIdManual] = useState(false);
  const [addBorrowerForm, setAddBorrowerForm] = useState(emptyAddBorrowerForm());
  const [actionMsg, setActionMsg] = useState('');
  const [busySubProgramId, setBusySubProgramId] = useState<string | null>(null);
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState('');
  const [editForm, setEditForm] = useState({
    name: '',
    interestRate: '',
    marginPercent: '',
    maxTenureDays: '',
    subProgramLimit: '',
  });
  const [editBorrowerRow, setEditBorrowerRow] = useState<SubProgramBorrower | null>(null);
  const [editBorrowerForm, setEditBorrowerForm] = useState<BorrowerTermsForm>({
    borrowerLimit: '',
    ...DEFAULT_BORROWER_TERMS,
  });
  const [editBorrowerSaving, setEditBorrowerSaving] = useState(false);
  const [editBorrowerError, setEditBorrowerError] = useState('');

  const [form, setForm] = useState({
    programId: '',
    anchorId: '',
    name: '',
    flowType: 'PURCHASE_BILL_DISCOUNTING',
    interestRate: '',
    marginPercent: '',
    maxTenureDays: '',
    subProgramLimit: '',
  });

  const portalCaps = lenderLoanCapabilities(getStoredAuthUser()?.role);
  const createFormProgram = programs.find((p) => p.id === form.programId) ?? null;
  const detailProgram = detail ? programs.find((p) => p.id === detail.programId) : null;
  const detailAnchor = detail ? anchors.find((a) => a.id === detail.anchorId) : null;

  const subPrograms = useMemo(() => {
    return allSubPrograms.filter((sp) => {
      if (filterProgramId && sp.programId !== filterProgramId) return false;
      if (filterAnchorId && sp.anchorId !== filterAnchorId) return false;
      return true;
    });
  }, [allSubPrograms, filterProgramId, filterAnchorId]);

  const borrowerById = useMemo(() => {
    const map = new Map<string, Borrower>();
    for (const b of programBorrowersPick) map.set(b.id, b);
    return map;
  }, [programBorrowersPick]);

  function borrowerDisplayName(borrowerId: string): string {
    const b = borrowerById.get(borrowerId);
    if (!b) return borrowerId;
    return b.name?.trim() || b.borrowerCode?.trim() || borrowerId;
  }

  function borrowerDisplayCode(borrowerId: string): string | null {
    const b = borrowerById.get(borrowerId);
    return b?.borrowerCode?.trim() || null;
  }

  const refreshSubProgramList = () => {
    setListRefreshing(true);
    subProgramApi
      .list()
      .then((r) => setAllSubPrograms(r.data.data || []))
      .catch(console.error)
      .finally(() => setListRefreshing(false));
  };

  useEffect(() => {
    Promise.all([
      programApi.list().then((r) => setPrograms(r.data.data || [])),
      anchorApi.list().then((r) => setAnchors(r.data.data || [])),
      subProgramApi.list().then((r) => setAllSubPrograms(r.data.data || [])),
    ])
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const loadDetailBorrowers = (subProgramId: string) => {
    subProgramApi
      .listBorrowers(subProgramId)
      .then((r) => setDetailBorrowers(r.data.data || []))
      .catch(console.error);
  };

  useEffect(() => {
    if (!detail) {
      setDetailBorrowers([]);
      setProgramBorrowersPick([]);
      setDetailSubview('overview');
      setAddBorrowerForm(emptyAddBorrowerForm());
      setBorrowerIdManual(false);
      setAddBorrowerError('');
      setEditBorrowerRow(null);
      return;
    }
    setDetailBorrowersLoading(true);
    Promise.all([
      subProgramApi.listBorrowers(detail.id).then((r) => setDetailBorrowers(r.data.data || [])),
      borrowerApi.list().then((r) => setProgramBorrowersPick(r.data.data || [])),
    ])
      .catch(console.error)
      .finally(() => setDetailBorrowersLoading(false));
  }, [detail]);

  const openDetail = (sp: SubProgram) => {
    setDetailSubview('overview');
    setDetail(sp);
  };

  const goToDetailOverview = () => {
    setDetailSubview('overview');
    setEditError('');
    setAddBorrowerError('');
    setEditBorrowerError('');
    setEditBorrowerRow(null);
  };

  const openEdit = () => {
    if (!detail) return;
    setEditError('');
    setEditForm({
      name: detail.name,
      interestRate: detail.interestRate != null ? String(detail.interestRate) : '',
      marginPercent: detail.marginPercent != null ? String(detail.marginPercent) : '',
      maxTenureDays: detail.maxTenureDays != null ? String(detail.maxTenureDays) : '',
      subProgramLimit: detail.subProgramLimit != null ? String(detail.subProgramLimit) : '',
    });
    setDetailSubview('edit');
  };

  const openEditBorrower = (row: SubProgramBorrower) => {
    setEditBorrowerError('');
    setEditBorrowerRow(row);
    setEditBorrowerForm(borrowerTermsFormFromMembership(row));
    setDetailSubview('edit-borrower');
  };

  const openAddBorrower = () => {
    setAddBorrowerError('');
    setAddBorrowerForm(emptyAddBorrowerForm());
    setBorrowerIdManual(false);
    setDetailSubview('add-borrower');
  };

  const handleEditSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail) return;
    setEditSaving(true);
    setEditError('');
    try {
      const payload: Record<string, unknown> = {
        name: editForm.name.trim(),
      };
      if (editForm.interestRate.trim()) payload.interestRate = parseFloat(editForm.interestRate);
      if (editForm.marginPercent.trim()) payload.marginPercent = parseFloat(editForm.marginPercent);
      if (editForm.maxTenureDays.trim()) payload.maxTenureDays = parseInt(editForm.maxTenureDays, 10);
      if (detail.status === 'DRAFT' && editForm.subProgramLimit.trim()) {
        payload.subProgramLimit = parseFloat(editForm.subProgramLimit);
      }
      const res = await subProgramApi.update(detail.id, payload);
      const updated = res.data.data as SubProgram;
      setDetail(updated);
      goToDetailOverview();
      refreshSubProgramList();
      setActionMsg('Sub-program updated.');
    } catch (err: unknown) {
      setEditError(extractApiErrorMessage(err, 'Failed to update sub-program'));
    } finally {
      setEditSaving(false);
    }
  };

  const handleEditBorrowerSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail || !editBorrowerRow) return;
    setEditBorrowerSaving(true);
    setEditBorrowerError('');
    try {
      const payload: Record<string, unknown> = {};
      appendBorrowerTermsToPayload(payload, editBorrowerForm, { includeLimit: true });
      const res = await subProgramApi.updateBorrowerTerms(detail.id, editBorrowerRow.borrowerId, payload);
      const updated = res.data.data as SubProgramBorrower;
      setDetailBorrowers((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
      goToDetailOverview();
      setActionMsg('Borrower terms updated.');
    } catch (err: unknown) {
      setEditBorrowerError(extractApiErrorMessage(err, 'Failed to update borrower terms'));
    } finally {
      setEditBorrowerSaving(false);
    }
  };

  const closeDetail = () => {
    setDetail(null);
    setDetailSubview('overview');
  };

  const handleAddBorrower = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail) return;
    const limit = parseFloat(addBorrowerForm.borrowerLimit);
    if (!addBorrowerForm.borrowerId.trim() || Number.isNaN(limit) || limit < 0) {
      setAddBorrowerError('Valid borrower and limit are required');
      return;
    }
    setAddBorrowerSubmitting(true);
    setAddBorrowerError('');
    try {
      const payload: Record<string, unknown> = {
        borrowerId: addBorrowerForm.borrowerId.trim(),
        borrowerLimit: limit,
        utilizedLimit: 0,
        availableLimit: limit,
        status: 'ACTIVE',
      };
      appendBorrowerTermsToPayload(payload, addBorrowerForm, { onCreate: true });
      await subProgramApi.addBorrower(detail.id, payload);
      setAddBorrowerForm(emptyAddBorrowerForm());
      setBorrowerIdManual(false);
      loadDetailBorrowers(detail.id);
      goToDetailOverview();
      setActionMsg('Borrower added to sub-program.');
    } catch (err: unknown) {
      setAddBorrowerError(extractApiErrorMessage(err, 'Failed to add borrower'));
    } finally {
      setAddBorrowerSubmitting(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError('');
    const program = programs.find((p) => p.id === form.programId);
    if (!program) {
      setError('Select a program');
      setCreating(false);
      return;
    }
    let flowType = form.flowType;
    let anchorRole: string;
    let borrowerRole: string;
    if (program.productType === 'PAY_DAY_LOAN') {
      flowType = 'PAY_LOAN';
      anchorRole = 'EMPLOYER';
      borrowerRole = 'EMPLOYEE';
    } else if (program.productType === 'INVOICE_DISCOUNTING') {
      const r = rolesForInvoiceDiscountingFlow(form.flowType);
      anchorRole = r.anchorRole;
      borrowerRole = r.borrowerRole;
    } else {
      setError('Sub-programs are only available for Invoice Discounting or Pay Day Loan programs.');
      setCreating(false);
      return;
    }
    try {
      await subProgramApi.create({
        programId: form.programId,
        anchorId: form.anchorId,
        name: form.name.trim(),
        flowType,
        anchorRole,
        borrowerRole,
        interestRate: parseFloat(form.interestRate),
        marginPercent: parseFloat(form.marginPercent),
        maxTenureDays: parseInt(form.maxTenureDays, 10),
        subProgramLimit: parseFloat(form.subProgramLimit),
      });
      setShowCreate(false);
      const selProg = programs.find((p) => p.id === form.programId);
      setForm({
        programId: form.programId,
        anchorId: '',
        name: '',
        flowType: selProg?.productType === 'PAY_DAY_LOAN' ? 'PAY_LOAN' : 'PURCHASE_BILL_DISCOUNTING',
        interestRate: '',
        marginPercent: '',
        maxTenureDays: '',
        subProgramLimit: '',
      });
      refreshSubProgramList();
    } catch (err: unknown) {
      setError(extractApiErrorMessage(err, 'Failed to create sub-program'));
    } finally {
      setCreating(false);
    }
  };

  const handleApproveSubProgram = async (sp: SubProgram) => {
    setActionMsg('');
    setBusySubProgramId(sp.id);
    try {
      await subProgramApi.approve(sp.id);
      setActionMsg(`Sub-program "${sp.code}" approved and set to ACTIVE`);
      refreshSubProgramList();
      setDetail((d) => (d?.id === sp.id ? { ...d, status: 'ACTIVE' } : d));
    } catch (err: unknown) {
      setActionMsg(`Approve failed: ${extractApiErrorMessage(err, 'Request failed')}`);
    } finally {
      setBusySubProgramId(null);
    }
  };

  const handleDeactivateSubProgram = async (sp: SubProgram) => {
    setActionMsg('');
    setBusySubProgramId(sp.id);
    try {
      await subProgramApi.deactivate(sp.id);
      setActionMsg(`Sub-program "${sp.code}" deactivated`);
      refreshSubProgramList();
      setDetail((d) => (d?.id === sp.id ? { ...d, status: 'INACTIVE' } : d));
    } catch (err: unknown) {
      setActionMsg(`Deactivate failed: ${extractApiErrorMessage(err, 'Request failed')}`);
    } finally {
      setBusySubProgramId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading...</div>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Sub Programs</h1>
          <p className="text-sm text-slate-500 mt-1">Operating arrangements under each program</p>
        </div>
        {portalCaps.canCreateProgramArtifacts && (
          <button
            type="button"
            onClick={() => {
              setError('');
              const pid = filterProgramId || form.programId || programs[0]?.id || '';
              const p = programs.find((x) => x.id === pid);
              setForm((f) => ({
                ...f,
                programId: pid,
                flowType:
                  p?.productType === 'PAY_DAY_LOAN'
                    ? 'PAY_LOAN'
                    : ['PURCHASE_BILL_DISCOUNTING', 'SALES_BILL_DISCOUNTING'].includes(f.flowType)
                      ? f.flowType
                      : 'PURCHASE_BILL_DISCOUNTING',
              }));
              setShowCreate(true);
            }}
            className="inline-flex items-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 shadow-sm"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            Create Sub Program
          </button>
        )}
      </div>

      {actionMsg && (
        <div
          className={`mb-4 p-3 rounded-lg text-sm ${
            actionMsg.includes('failed')
              ? 'bg-red-50 text-red-700 border border-red-200'
              : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
          }`}
        >
          {actionMsg}
        </div>
      )}

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl">
          <div>
            <label className={labelCls}>Filter by anchor</label>
            <select value={filterAnchorId} onChange={(e) => setFilterAnchorId(e.target.value)} className={inputCls}>
              <option value="">All anchors</option>
              {anchors.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.anchorCode} — {a.entityName}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelCls}>Filter by program</label>
            <select value={filterProgramId} onChange={(e) => setFilterProgramId(e.target.value)} className={inputCls}>
              <option value="">All programs</option>
              {programs.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.programCode} — {p.programName}
                </option>
              ))}
            </select>
          </div>
        </div>
        {listRefreshing && <p className="mt-2 text-xs text-slate-500">Refreshing list…</p>}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/80 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
            Sub-programs ({subPrograms.length})
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Code</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Name</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Program</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Anchor</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Flow</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase">Limit</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase">Consumed</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase">Available</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Status</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Actions</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {subPrograms.map((sp) => {
                const prog = programs.find((p) => p.id === sp.programId);
                const anc = anchors.find((a) => a.id === sp.anchorId);
                const cap = sp.subProgramLimit != null ? Number(sp.subProgramLimit) : NaN;
                const consumed =
                  sp.utilizedLimit != null
                    ? Number(sp.utilizedLimit)
                    : Number.isFinite(cap) && sp.availableLimit != null
                      ? Math.max(0, cap - Number(sp.availableLimit))
                      : null;
                const available =
                  sp.availableLimit != null
                    ? Number(sp.availableLimit)
                    : Number.isFinite(cap) && consumed != null
                      ? Math.max(0, cap - consumed)
                      : null;
                return (
                  <tr key={sp.id} className="hover:bg-slate-50/80">
                    <td className="px-5 py-3 font-mono text-xs text-slate-600">{sp.code}</td>
                    <td className="px-5 py-3 font-medium text-slate-800">{sp.name}</td>
                    <td className="px-5 py-3 text-xs text-slate-700">
                      {prog ? (
                        <>
                          <div className="font-medium">{prog.programName}</div>
                          <div className="text-slate-400 font-mono">{prog.programCode}</div>
                        </>
                      ) : (
                        <span className="font-mono text-slate-500">{sp.programId}</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-xs text-slate-700">
                      {anc ? (
                        <>
                          <div className="font-medium">{anc.entityName}</div>
                          <div className="text-slate-400 font-mono">{anc.anchorCode}</div>
                        </>
                      ) : (
                        <span className="font-mono text-slate-500">{sp.anchorId}</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-xs text-slate-600">{subProgramFlowLabel(sp.flowType)}</td>
                    <td className="px-5 py-3 text-right tabular-nums">{formatInrAmount(sp.subProgramLimit ?? null)}</td>
                    <td className="px-5 py-3 text-right tabular-nums">{formatInrAmount(consumed)}</td>
                    <td className="px-5 py-3 text-right tabular-nums">{formatInrAmount(available)}</td>
                    <td className="px-5 py-3 text-center">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-md text-xs font-semibold ${subProgramStatusBadgeClass(sp.status)}`}
                      >
                        {sp.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-center">
                      <div className="flex items-center justify-center gap-1.5 flex-wrap">
                        {(() => {
                          const showApprove = sp.status === 'DRAFT' && portalCaps.canApproveProgramArtifacts;
                          const showDeactivate = sp.status === 'ACTIVE' && portalCaps.canApproveProgramArtifacts;
                          if (showApprove) {
                            return (
                              <button
                                type="button"
                                disabled={busySubProgramId === sp.id}
                                onClick={() => void handleApproveSubProgram(sp)}
                                className="px-2.5 py-1 text-xs font-semibold text-emerald-700 bg-emerald-50 rounded hover:bg-emerald-100 disabled:opacity-50"
                              >
                                {busySubProgramId === sp.id ? '…' : 'Approve'}
                              </button>
                            );
                          }
                          if (showDeactivate) {
                            return (
                              <button
                                type="button"
                                disabled={busySubProgramId === sp.id}
                                onClick={() => void handleDeactivateSubProgram(sp)}
                                className="px-2.5 py-1 text-xs font-semibold text-slate-600 bg-slate-100 rounded hover:bg-slate-200 disabled:opacity-50"
                              >
                                {busySubProgramId === sp.id ? '…' : 'Deactivate'}
                              </button>
                            );
                          }
                          return <span className="text-xs text-slate-400">—</span>;
                        })()}
                      </div>
                    </td>
                    <td className="px-5 py-3 text-center">
                      <button
                        type="button"
                        onClick={() => openDetail(sp)}
                        className="text-xs font-semibold text-blue-600 hover:text-blue-800"
                      >
                        View
                      </button>
                    </td>
                  </tr>
                );
              })}
              {subPrograms.length === 0 && (
                <tr>
                  <td colSpan={11} className="px-5 py-12 text-center text-slate-400 text-sm">
                    No sub-programs match the current filters
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showCreate && (
        <CreateFormModal
          title="Create Sub Program"
          onClose={() => setShowCreate(false)}
          onSubmit={(e) => void handleCreate(e)}
          submitting={creating}
          submitLabel="Create"
          submittingLabel="Creating…"
          error={error}
        >
          <div className={formGridCls}>
            <div className="col-span-full">
              <label className={labelCls}>Program</label>
              <select
                required
                value={form.programId}
                onChange={(e) => {
                  const pid = e.target.value;
                  const p = programs.find((x) => x.id === pid);
                  setForm((f) => ({
                    ...f,
                    programId: pid,
                    ...(p?.productType === 'PAY_DAY_LOAN'
                      ? { flowType: 'PAY_LOAN' }
                      : !['PURCHASE_BILL_DISCOUNTING', 'SALES_BILL_DISCOUNTING'].includes(f.flowType)
                        ? { flowType: 'PURCHASE_BILL_DISCOUNTING' }
                        : {}),
                  }));
                }}
                className={inputCls}
              >
                <option value="">Select program</option>
                {programs.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.programCode} — {p.programName}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-span-full">
              <label className={labelCls}>Anchor</label>
              <select
                required
                value={form.anchorId}
                onChange={(e) => setForm({ ...form, anchorId: e.target.value })}
                className={inputCls}
              >
                <option value="">Select anchor</option>
                {anchors.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.anchorCode} — {a.entityName}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-span-full">
              <label className={labelCls}>Name</label>
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputCls}
              />
              <p className="text-xs text-slate-500 mt-1">Sub-program code is generated automatically when you save.</p>
            </div>
            {createFormProgram?.productType === 'PAY_DAY_LOAN' ? (
              <div className="col-span-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700">
                <span className="font-medium text-slate-800">Flow:</span> Pay Loan
                <p className="text-xs text-slate-500 mt-1">
                  Anchor role Employer and borrower role Employee are sent automatically.
                </p>
              </div>
            ) : createFormProgram?.productType === 'INVOICE_DISCOUNTING' ? (
              <div className="col-span-full">
                <label className={labelCls}>Flow type</label>
                <select
                  value={form.flowType}
                  onChange={(e) => setForm({ ...form, flowType: e.target.value })}
                  className={inputCls}
                >
                  <option value="PURCHASE_BILL_DISCOUNTING">Purchase Bill Discounting</option>
                  <option value="SALES_BILL_DISCOUNTING">Sales Bill Discounting</option>
                </select>
              </div>
            ) : (
              <p className="col-span-full text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                Select an Invoice Discounting or Pay Day Loan program to configure flow and roles.
              </p>
            )}
            <div>
              <label className={labelCls}>Interest rate (%)</label>
              <input
                required
                type="number"
                step="0.0001"
                value={form.interestRate}
                onChange={(e) => setForm({ ...form, interestRate: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Margin (%)</label>
              <input
                required
                type="number"
                step="0.0001"
                value={form.marginPercent}
                onChange={(e) => setForm({ ...form, marginPercent: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Max tenure (days)</label>
              <input
                required
                type="number"
                min={1}
                value={form.maxTenureDays}
                onChange={(e) => setForm({ ...form, maxTenureDays: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Sub-program limit</label>
              <input
                required
                type="number"
                step="0.01"
                min={0}
                value={form.subProgramLimit}
                onChange={(e) => setForm({ ...form, subProgramLimit: e.target.value })}
                className={inputCls}
              />
            </div>
          </div>
        </CreateFormModal>
      )}

      {detail && (
        <DetailModalShell
          title={
            detailSubview === 'edit'
              ? 'Edit sub-program'
              : detailSubview === 'add-borrower'
                ? 'Add borrower'
                : detailSubview === 'edit-borrower'
                  ? 'Edit borrower'
                  : detail.name
          }
          subtitle={
            detailSubview === 'overview'
              ? `${detail.code} · ${subProgramFlowLabel(detail.flowType)}`
              : detailSubview === 'edit-borrower' && editBorrowerRow
                ? borrowerDisplayName(editBorrowerRow.borrowerId)
                : detail.code
          }
          onClose={closeDetail}
          onBack={detailSubview !== 'overview' ? goToDetailOverview : undefined}
          footer={
            detailSubview === 'edit' ? (
              <>
                <button type="button" onClick={goToDetailOverview} className="bt-btn bt-btn-secondary">
                  Cancel
                </button>
                <BtButton type="submit" form="detail-edit-sp-form" disabled={editSaving}>
                  {editSaving ? 'Saving…' : 'Save changes'}
                </BtButton>
              </>
            ) : detailSubview === 'add-borrower' ? (
              <>
                <button type="button" onClick={goToDetailOverview} className="bt-btn bt-btn-secondary">
                  Cancel
                </button>
                <BtButton type="submit" form="detail-add-borrower-form" disabled={addBorrowerSubmitting}>
                  {addBorrowerSubmitting ? 'Adding…' : 'Add borrower'}
                </BtButton>
              </>
            ) : detailSubview === 'edit-borrower' ? (
              <>
                <button type="button" onClick={goToDetailOverview} className="bt-btn bt-btn-secondary">
                  Cancel
                </button>
                <BtButton type="submit" form="detail-edit-borrower-form" disabled={editBorrowerSaving}>
                  {editBorrowerSaving ? 'Saving…' : 'Save borrower'}
                </BtButton>
              </>
            ) : undefined
          }
        >
          {detailSubview === 'overview' ? (
            <div className={modalBodyScrollCls}>
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between mb-6">
                <div className="flex flex-wrap items-center gap-2">
                  <BtBadge status={detail.status}>{detail.status}</BtBadge>
                  {detailProgram ? (
                    <span className="text-xs text-slate-600 bg-slate-100 px-2.5 py-1 rounded-full">
                      {detailProgram.programName}
                    </span>
                  ) : null}
                  {detailAnchor ? (
                    <span className="text-xs text-slate-600 bg-slate-100 px-2.5 py-1 rounded-full">
                      {detailAnchor.entityName}
                    </span>
                  ) : null}
                </div>
                {portalCaps.canEditProgramConfig && detail.status !== 'INACTIVE' ? (
                  <button type="button" onClick={openEdit} className="bt-btn bt-btn-secondary text-sm">
                    Edit sub-program
                  </button>
                ) : null}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
                <LimitStat label="Sub-program limit" value={formatInrAmount(detail.subProgramLimit)} />
                <LimitStat label="Utilized" value={formatInrAmount(detail.utilizedLimit)} tone="amber" />
                <LimitStat label="Available" value={formatInrAmount(detail.availableLimit)} tone="green" />
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
                <TermsGrid
                  title="Pricing & tenure"
                  rows={[
                    { label: 'Interest rate', value: detail.interestRate != null ? `${detail.interestRate}%` : '—' },
                    { label: 'Discount margin', value: detail.marginPercent != null ? `${detail.marginPercent}%` : '—' },
                    {
                      label: 'Max tenure',
                      value: detail.maxTenureDays != null ? `${detail.maxTenureDays} days` : '—',
                    },
                  ]}
                />
                <TermsGrid
                  title="Flow & roles"
                  rows={[
                    { label: 'Flow type', value: subProgramFlowLabel(detail.flowType) },
                    { label: 'Anchor role', value: detail.anchorRole ?? '—' },
                    { label: 'Borrower role', value: detail.borrowerRole ?? '—' },
                    ...(buildSubProgramConfigurationRows(detail).filter(
                      (r) => !['Flow type', 'Anchor role', 'Borrower role', 'Interest rate', 'Discount margin', 'Max tenure (days)', 'Sub-program limit'].includes(r.label),
                    )),
                  ]}
                />
              </div>

              <div className="border-t border-slate-200 pt-8">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-5">
                  <div>
                    <h3 className="text-base font-semibold text-slate-800">Enrolled borrowers</h3>
                    <p className="text-sm text-slate-500 mt-0.5">
                      {detailBorrowers.length} borrower{detailBorrowers.length === 1 ? '' : 's'} on this sub-program
                    </p>
                  </div>
                  {portalCaps.canCreateProgramArtifacts ? (
                    <button type="button" onClick={openAddBorrower} className="bt-btn bt-btn-primary text-sm">
                      Add borrower
                    </button>
                  ) : null}
                </div>

                {detailBorrowersLoading ? (
                  <div className="text-sm text-slate-400 py-12 text-center rounded-xl border border-dashed border-slate-200">
                    Loading borrowers…
                  </div>
                ) : detailBorrowers.length === 0 ? (
                  <div className="text-sm text-slate-400 py-12 text-center rounded-xl border border-dashed border-slate-200">
                    No borrowers enrolled yet.
                  </div>
                ) : (
                  <div className="space-y-4">
                    {detailBorrowers.map((row) => (
                      <BorrowerMembershipCard
                        key={row.id}
                        subProgramId={detail.id}
                        row={row}
                        displayName={borrowerDisplayName(row.borrowerId)}
                        displayCode={borrowerDisplayCode(row.borrowerId)}
                        canEdit={portalCaps.canEditProgramConfig}
                        onEdit={() => openEditBorrower(row)}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : detailSubview === 'edit' ? (
            <form id="detail-edit-sp-form" onSubmit={(e) => void handleEditSave(e)} className={modalFormCls}>
              <div className={modalBodyScrollCls}>
                {editError ? (
                  <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{editError}</div>
                ) : null}
                <div className={formGridCls}>
                  <FormField label="Name *" span={2}>
                    <input
                      className={inputCls}
                      value={editForm.name}
                      required
                      onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                    />
                  </FormField>
                  <FormField label="Interest rate (%)">
                    <input
                      type="number"
                      step="0.01"
                      className={inputCls}
                      value={editForm.interestRate}
                      onChange={(e) => setEditForm({ ...editForm, interestRate: e.target.value })}
                    />
                  </FormField>
                  <FormField label="Discount margin (%)">
                    <input
                      type="number"
                      step="0.01"
                      className={inputCls}
                      value={editForm.marginPercent}
                      onChange={(e) => setEditForm({ ...editForm, marginPercent: e.target.value })}
                    />
                  </FormField>
                  <FormField label="Max tenure (days)">
                    <input
                      type="number"
                      className={inputCls}
                      value={editForm.maxTenureDays}
                      onChange={(e) => setEditForm({ ...editForm, maxTenureDays: e.target.value })}
                    />
                  </FormField>
                  {detail.status === 'DRAFT' ? (
                    <FormField label="Sub-program limit">
                      <input
                        type="number"
                        step="0.01"
                        className={inputCls}
                        value={editForm.subProgramLimit}
                        onChange={(e) => setEditForm({ ...editForm, subProgramLimit: e.target.value })}
                      />
                    </FormField>
                  ) : null}
                </div>
              </div>
            </form>
          ) : detailSubview === 'add-borrower' ? (
            <form id="detail-add-borrower-form" onSubmit={(e) => void handleAddBorrower(e)} className={modalFormCls}>
              <div className={modalBodyScrollCls}>
                {addBorrowerError ? (
                  <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{addBorrowerError}</div>
                ) : null}
                <div className={formGridCls}>
                  <div className="col-span-full flex items-center gap-3 text-sm">
                    <button
                      type="button"
                      onClick={() => {
                        setBorrowerIdManual(false);
                        setAddBorrowerForm((f) => ({ ...f, borrowerId: '' }));
                      }}
                      className={`font-medium px-3 py-1.5 rounded-lg ${!borrowerIdManual ? 'bg-blue-50 text-blue-700' : 'text-slate-500 hover:bg-slate-50'}`}
                    >
                      Pick from list
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setBorrowerIdManual(true);
                        setAddBorrowerForm((f) => ({ ...f, borrowerId: '' }));
                      }}
                      className={`font-medium px-3 py-1.5 rounded-lg ${borrowerIdManual ? 'bg-blue-50 text-blue-700' : 'text-slate-500 hover:bg-slate-50'}`}
                    >
                      Enter UUID
                    </button>
                  </div>
                  {!borrowerIdManual ? (
                    <FormField label="Borrower *" span={2}>
                      <select
                        required
                        value={addBorrowerForm.borrowerId}
                        onChange={(e) => setAddBorrowerForm({ ...addBorrowerForm, borrowerId: e.target.value })}
                        className={inputCls}
                      >
                        <option value="">Select borrower</option>
                        {programBorrowersPick.map((b) => (
                          <option key={b.id} value={b.id}>
                            {b.borrowerCode} — {b.name}
                          </option>
                        ))}
                      </select>
                      {programBorrowersPick.length === 0 ? (
                        <p className="text-xs text-amber-600 mt-1">
                          No borrowers in catalog. Use UUID entry or create a borrower first.
                        </p>
                      ) : null}
                    </FormField>
                  ) : (
                    <FormField label="Borrower ID (UUID) *" span={2}>
                      <input
                        required
                        value={addBorrowerForm.borrowerId}
                        onChange={(e) => setAddBorrowerForm({ ...addBorrowerForm, borrowerId: e.target.value })}
                        className={`${inputCls} font-mono text-xs`}
                        placeholder="00000000-0000-0000-0000-000000000000"
                      />
                    </FormField>
                  )}
                  <BorrowerTermsFields
                    form={addBorrowerForm}
                    onChange={(next) => setAddBorrowerForm({ ...addBorrowerForm, ...next })}
                    limitRequired
                  />
                </div>
              </div>
            </form>
          ) : detailSubview === 'edit-borrower' && editBorrowerRow ? (
            <form id="detail-edit-borrower-form" onSubmit={(e) => void handleEditBorrowerSave(e)} className={modalFormCls}>
              <div className={modalBodyScrollCls}>
                {editBorrowerError ? (
                  <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{editBorrowerError}</div>
                ) : null}
                {!borrowerHasTermOverrides(editBorrowerRow) ? (
                  <p className="mb-5 text-sm text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-4 py-3">
                    This borrower inherits pricing from the sub-program. Set overrides below to customize.
                  </p>
                ) : null}
                <div className={formGridCls}>
                  <BorrowerTermsFields form={editBorrowerForm} onChange={setEditBorrowerForm} />
                </div>
              </div>
            </form>
          ) : null}
        </DetailModalShell>
      )}
    </div>
  );
}
