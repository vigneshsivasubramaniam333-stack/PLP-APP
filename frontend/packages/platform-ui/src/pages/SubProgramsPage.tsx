import { useEffect, useMemo, useState } from 'react';
import {
  programApi,
  anchorApi,
  subProgramApi,
  borrowerApi,
  extractApiErrorMessage,
  getStoredAuthUser,
  lenderLoanCapabilities,
} from '@plp/shared';
import type { Program, Anchor, SubProgram, SubProgramBorrower, Borrower } from '@plp/shared';

const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

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
  const [detailBorrowers, setDetailBorrowers] = useState<SubProgramBorrower[]>([]);
  const [detailBorrowersLoading, setDetailBorrowersLoading] = useState(false);
  const [programBorrowersPick, setProgramBorrowersPick] = useState<Borrower[]>([]);
  const [showAddBorrower, setShowAddBorrower] = useState(false);
  const [addBorrowerSubmitting, setAddBorrowerSubmitting] = useState(false);
  const [addBorrowerError, setAddBorrowerError] = useState('');
  const [borrowerIdManual, setBorrowerIdManual] = useState(false);
  const [addBorrowerForm, setAddBorrowerForm] = useState({ borrowerId: '', borrowerLimit: '' });
  const [actionMsg, setActionMsg] = useState('');
  const [busySubProgramId, setBusySubProgramId] = useState<string | null>(null);

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

  const subPrograms = useMemo(() => {
    return allSubPrograms.filter((sp) => {
      if (filterProgramId && sp.programId !== filterProgramId) return false;
      if (filterAnchorId && sp.anchorId !== filterAnchorId) return false;
      return true;
    });
  }, [allSubPrograms, filterProgramId, filterAnchorId]);

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
      setShowAddBorrower(false);
      setAddBorrowerForm({ borrowerId: '', borrowerLimit: '' });
      setBorrowerIdManual(false);
      setAddBorrowerError('');
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
    setDetail(sp);
  };

  const closeDetail = () => {
    setDetail(null);
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
      await subProgramApi.addBorrower(detail.id, {
        borrowerId: addBorrowerForm.borrowerId.trim(),
        borrowerLimit: limit,
        utilizedLimit: 0,
        availableLimit: limit,
        status: 'ACTIVE',
      });
      setShowAddBorrower(false);
      setAddBorrowerForm({ borrowerId: '', borrowerLimit: '' });
      setBorrowerIdManual(false);
      loadDetailBorrowers(detail.id);
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

  const formatNum = (v: number | null | undefined) =>
    v == null || Number.isNaN(Number(v)) ? '—' : Number(v).toLocaleString('en-IN');

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
            <select
              value={filterAnchorId}
              onChange={(e) => setFilterAnchorId(e.target.value)}
              className={inputCls}
            >
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
            <select
              value={filterProgramId}
              onChange={(e) => setFilterProgramId(e.target.value)}
              className={inputCls}
            >
              <option value="">All programs</option>
              {programs.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.programCode} — {p.programName}
                </option>
              ))}
            </select>
          </div>
        </div>
        {listRefreshing && (
          <p className="mt-2 text-xs text-slate-500">Refreshing list…</p>
        )}
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
                          const showApprove =
                            sp.status === 'DRAFT' && portalCaps.canApproveProgramArtifacts;
                          const showDeactivate =
                            sp.status === 'ACTIVE' && portalCaps.canApproveProgramArtifacts;
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
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center">
              <h2 className="text-lg font-semibold text-slate-800">Create Sub Program</h2>
              <button
                type="button"
                onClick={() => setShowCreate(false)}
                className="text-slate-400 hover:text-slate-600 text-xl leading-none"
              >
                ×
              </button>
            </div>
            <form onSubmit={handleCreate} className="p-6 space-y-4">
              {error && (
                <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</div>
              )}
              <div>
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
              <div>
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
              <div>
                <label className={labelCls}>Name</label>
                <input
                  required
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className={inputCls}
                />
              </div>
              <p className="text-xs text-slate-500">
                Sub-program code is generated automatically when you save.
              </p>
              {createFormProgram?.productType === 'PAY_DAY_LOAN' ? (
                <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700">
                  <span className="font-medium text-slate-800">Flow:</span> Pay Loan
                  <p className="text-xs text-slate-500 mt-1">
                    Anchor role Employer and borrower role Employee are sent automatically.
                  </p>
                </div>
              ) : createFormProgram?.productType === 'INVOICE_DISCOUNTING' ? (
                <div>
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
                <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  Select an Invoice Discounting or Pay Day Loan program to configure flow and roles.
                </p>
              )}
              <div className="grid grid-cols-2 gap-3">
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
              <div className="flex gap-3 pt-2">
                <button
                  type="submit"
                  disabled={creating}
                  className="flex-1 bg-blue-600 text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
                >
                  {creating ? 'Creating...' : 'Create'}
                </button>
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  className="px-4 py-2.5 rounded-lg text-sm font-medium text-slate-600 border border-slate-200 hover:bg-slate-50"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center sticky top-0 bg-white z-10">
              <h2 className="text-lg font-semibold text-slate-800">Sub-program details</h2>
              <button
                type="button"
                onClick={closeDetail}
                className="text-slate-400 hover:text-slate-600 text-xl leading-none"
              >
                ×
              </button>
            </div>
            <div className="p-6 space-y-3 text-sm border-b border-slate-100">
              <DetailRow label="ID" value={detail.id} mono />
              <DetailRow label="Name" value={detail.name} />
              <DetailRow label="Code" value={detail.code} mono />
              <DetailRow label="Program ID" value={detail.programId} mono />
              <DetailRow label="Anchor ID" value={detail.anchorId} mono />
              <DetailRow label="Flow type" value={subProgramFlowLabel(detail.flowType)} />
              <DetailRow label="Anchor role" value={detail.anchorRole} />
              <DetailRow label="Borrower role" value={detail.borrowerRole} />
              <DetailRow label="Interest %" value={formatNum(detail.interestRate)} />
              <DetailRow label="Margin %" value={formatNum(detail.marginPercent)} />
              <DetailRow label="Max tenure (days)" value={detail.maxTenureDays ?? '—'} />
              <DetailRow label="Limit" value={formatNum(detail.subProgramLimit)} />
              <DetailRow label="Utilized" value={formatNum(detail.utilizedLimit)} />
              <DetailRow label="Available" value={formatNum(detail.availableLimit)} />
              <DetailRow label="Status" value={detail.status} />
            </div>

            <div className="p-6">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-slate-800">Borrowers in sub-program</h3>
                {portalCaps.canCreateProgramArtifacts ? (
                  <button
                    type="button"
                    onClick={() => {
                      setAddBorrowerError('');
                      setAddBorrowerForm({ borrowerId: '', borrowerLimit: '' });
                      setBorrowerIdManual(false);
                      setShowAddBorrower(true);
                    }}
                    className="text-xs font-semibold text-blue-600 hover:text-blue-800 bg-blue-50 px-3 py-1.5 rounded-lg"
                  >
                    Add Borrower
                  </button>
                ) : (
                  <span className="text-xs text-slate-400">—</span>
                )}
              </div>
              {detailBorrowersLoading ? (
                <div className="text-sm text-slate-400 py-6 text-center">Loading borrowers...</div>
              ) : (
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200">
                        <th className="px-4 py-2.5 text-left text-xs font-semibold text-slate-500 uppercase">
                          Borrower ID
                        </th>
                        <th className="px-4 py-2.5 text-right text-xs font-semibold text-slate-500 uppercase">
                          Limit
                        </th>
                        <th className="px-4 py-2.5 text-right text-xs font-semibold text-slate-500 uppercase">
                          Utilized
                        </th>
                        <th className="px-4 py-2.5 text-right text-xs font-semibold text-slate-500 uppercase">
                          Available
                        </th>
                        <th className="px-4 py-2.5 text-center text-xs font-semibold text-slate-500 uppercase">
                          Status
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {detailBorrowers.map((row) => (
                        <tr key={row.id} className="hover:bg-slate-50/80">
                          <td className="px-4 py-2.5 font-mono text-xs text-slate-700">{row.borrowerId}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{formatNum(row.borrowerLimit)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{formatNum(row.utilizedLimit)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{formatNum(row.availableLimit)}</td>
                          <td className="px-4 py-2.5 text-center">
                            <span className="inline-flex px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                              {row.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                      {detailBorrowers.length === 0 && (
                        <tr>
                          <td colSpan={5} className="px-4 py-8 text-center text-slate-400 text-sm">
                            No borrowers enrolled
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {detail && showAddBorrower && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center">
              <h2 className="text-lg font-semibold text-slate-800">Add borrower to sub-program</h2>
              <button
                type="button"
                onClick={() => setShowAddBorrower(false)}
                className="text-slate-400 hover:text-slate-600 text-xl leading-none"
              >
                ×
              </button>
            </div>
            <form onSubmit={handleAddBorrower} className="p-6 space-y-4">
              {addBorrowerError && (
                <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  {addBorrowerError}
                </div>
              )}
              <div className="flex items-center gap-2 text-xs">
                <button
                  type="button"
                  onClick={() => {
                    setBorrowerIdManual(false);
                    setAddBorrowerForm((f) => ({ ...f, borrowerId: '' }));
                  }}
                  className={`font-medium ${!borrowerIdManual ? 'text-blue-600' : 'text-slate-500'}`}
                >
                  Pick from borrowers
                </button>
                <span className="text-slate-300">|</span>
                <button
                  type="button"
                  onClick={() => {
                    setBorrowerIdManual(true);
                    setAddBorrowerForm((f) => ({ ...f, borrowerId: '' }));
                  }}
                  className={`font-medium ${borrowerIdManual ? 'text-blue-600' : 'text-slate-500'}`}
                >
                  Enter UUID
                </button>
              </div>
              {!borrowerIdManual ? (
                <div>
                  <label className={labelCls}>Borrower</label>
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
                  {programBorrowersPick.length === 0 && (
                    <p className="text-xs text-amber-600 mt-1">No borrowers in the catalog. Enter a borrower UUID or create one first.</p>
                  )}
                </div>
              ) : (
                <div>
                  <label className={labelCls}>Borrower ID (UUID)</label>
                  <input
                    required
                    value={addBorrowerForm.borrowerId}
                    onChange={(e) => setAddBorrowerForm({ ...addBorrowerForm, borrowerId: e.target.value })}
                    className={`${inputCls} font-mono text-xs`}
                    placeholder="00000000-0000-0000-0000-000000000000"
                  />
                </div>
              )}
              <div>
                <label className={labelCls}>Borrower limit</label>
                <input
                  required
                  type="number"
                  step="0.01"
                  min={0}
                  value={addBorrowerForm.borrowerLimit}
                  onChange={(e) => setAddBorrowerForm({ ...addBorrowerForm, borrowerLimit: e.target.value })}
                  className={inputCls}
                />
                <p className="text-[11px] text-slate-400 mt-1">
                  Utilized starts at 0; available equals limit until financing uses capacity.
                </p>
              </div>
              <div className="flex gap-3 pt-1">
                <button
                  type="submit"
                  disabled={addBorrowerSubmitting}
                  className="flex-1 bg-blue-600 text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
                >
                  {addBorrowerSubmitting ? 'Adding...' : 'Add borrower'}
                </button>
                <button
                  type="button"
                  onClick={() => setShowAddBorrower(false)}
                  className="px-4 py-2.5 rounded-lg text-sm font-medium text-slate-600 border border-slate-200 hover:bg-slate-50"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="flex gap-3">
      <span className="text-slate-500 w-36 shrink-0">{label}</span>
      <span className={`text-slate-800 ${mono ? 'font-mono text-xs break-all' : ''}`}>{value}</span>
    </div>
  );
}
