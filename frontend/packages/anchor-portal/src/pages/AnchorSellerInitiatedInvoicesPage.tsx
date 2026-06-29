import { useState, useEffect, useMemo } from 'react';
import {
  programApi,
  portalApi,
  subProgramApi,
  useAuth,
  DigitalInvoiceAttachment,
  BtPageHeader,
  BtCard,
  BtCardHeader,
  BtButton,
  BtBadge,
  InvoiceLinkedLoansPanel,
  notifyError,
  notifySuccess,
  flowTypeLabel,
  type InvoiceDiscountingFlowType,
} from '@plp/shared';
import type { Program, Invoice, SubProgram } from '@plp/shared';
import {
  anchorIdFromUser,
  isInvoiceDiscountingSubProgramForFlow,
  formatInvoiceCurrency,
  inputCls,
  labelCls,
} from '../invoice/invoiceShared';

type Tab = 'pending' | 'approved' | 'rejected' | 'closed';

type Props = {
  flowType: InvoiceDiscountingFlowType;
};

export default function AnchorSellerInitiatedInvoicesPage({ flowType }: Props) {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [tab, setTab] = useState<Tab>('pending');
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [actingId, setActingId] = useState<string | null>(null);
  const [expandedLoanInvoiceIds, setExpandedLoanInvoiceIds] = useState<Set<string>>(() => new Set());

  const toggleLoanDetails = (invoiceId: string) => {
    setExpandedLoanInvoiceIds((prev) => {
      const next = new Set(prev);
      if (next.has(invoiceId)) next.delete(invoiceId);
      else next.add(invoiceId);
      return next;
    });
  };

  const idSubPrograms = useMemo(
    () => subPrograms.filter((sp) => isInvoiceDiscountingSubProgramForFlow(sp, programs, flowType)),
    [subPrograms, programs, flowType],
  );

  const umbrellaProgramId = useMemo(() => {
    const sp = idSubPrograms.find((s) => s.id === selectedSubProgramId);
    return sp?.programId ?? '';
  }, [idSubPrograms, selectedSubProgramId]);

  useEffect(() => {
    Promise.all([
      programApi.list().then((r) => setPrograms(r.data.data || [])),
      anchorId ? subProgramApi.list().then((r) => setSubPrograms(r.data.data || [])) : Promise.resolve(),
    ]).catch(console.error);
  }, [anchorId]);

  const loadInvoices = () => {
    if (!anchorId) return;
    setLoading(true);
    portalApi
      .anchorInvoices(anchorId, {
        programId: umbrellaProgramId || undefined,
        flowType,
        tab: tab === 'closed' ? undefined : tab,
        lifecycle: tab === 'approved' ? 'active' : tab === 'closed' ? 'closed' : undefined,
        page: 0,
        size: 100,
      })
      .then((r) => setInvoices(r.data.data || []))
      .catch(() => setInvoices([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (anchorId) loadInvoices();
  }, [anchorId, umbrellaProgramId, tab, flowType]);

  const handleApprove = async (id: string) => {
    setActingId(id);
    try {
      await portalApi.anchorApproveSellerInvoice(id);
      notifySuccess('Invoice approved');
      loadInvoices();
    } catch (err) {
      notifyError(err, 'Could not approve invoice.');
    } finally {
      setActingId(null);
    }
  };

  const handleReject = async () => {
    if (!rejectId) return;
    setActingId(rejectId);
    try {
      await portalApi.anchorRejectSellerInvoice(rejectId, rejectReason || undefined);
      notifySuccess('Invoice rejected');
      setRejectId(null);
      setRejectReason('');
      loadInvoices();
    } catch (err) {
      notifyError(err, 'Could not reject invoice.');
    } finally {
      setActingId(null);
    }
  };

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">{flowTypeLabel(flowType)}</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <BtPageHeader
        title={flowTypeLabel(flowType)}
        description="Review borrower-submitted invoices. Approve eligible items or reject with a reason."
      />

      <BtCard className="mb-6 p-5">
        <div className="max-w-xl">
          <label className={labelCls}>Sub-program</label>
          <select
            value={selectedSubProgramId}
            onChange={(e) => setSelectedSubProgramId(e.target.value)}
            className={inputCls}
          >
            <option value="">All sub-programs</option>
            {idSubPrograms.map((sp) => {
              const parent = programs.find((p) => p.id === sp.programId);
              return (
                <option key={sp.id} value={sp.id}>
                  {sp.code} — {sp.name}
                  {parent ? ` (${parent.programCode})` : ''}
                </option>
              );
            })}
          </select>
        </div>
      </BtCard>

      <div className="mb-4 flex gap-2">
        {(['pending', 'approved', 'rejected', 'closed'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`bt-btn bt-btn-sm ${tab === t ? 'bt-btn-primary' : 'bt-btn-secondary'}`}
          >
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      <BtCard className="overflow-hidden p-0">
        <BtCardHeader title="Invoices" />
        {loading ? (
          <div className="px-5 py-16 text-center text-sm text-[var(--bt-gray-400)] animate-pulse">Loading…</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="bt-table w-full">
              <thead>
                <tr>
                  <th>Invoice #</th>
                  <th>Dates</th>
                  <th className="text-right">Net</th>
                  <th className="text-center">Copy</th>
                  <th className="text-center">Status</th>
                  {tab === 'pending' || tab === 'closed' ? <th className="text-center">Actions</th> : null}
                </tr>
              </thead>
              <tbody>
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={tab === 'pending' || tab === 'closed' ? 6 : 5} className="px-5 py-12 text-center text-sm text-[var(--bt-gray-400)]">
                      No invoices in this tab
                    </td>
                  </tr>
                ) : (
                  invoices.flatMap((inv) => {
                    const loanExpanded = expandedLoanInvoiceIds.has(inv.id);
                    const rows = [
                      <tr key={inv.id}>
                        <td className="font-mono text-xs">{inv.invoiceNumber}</td>
                        <td className="text-xs">
                          <div>{inv.invoiceDate}</div>
                          <div className="text-[var(--bt-gray-400)]">Due: {inv.dueDate}</div>
                        </td>
                        <td className="text-right tabular-nums">{formatInvoiceCurrency(inv.netAmount)}</td>
                        <td className="text-center">
                          <DigitalInvoiceAttachment invoiceId={inv.id} fileName={inv.digitalInvoiceFileName} />
                        </td>
                        <td className="text-center">
                          <BtBadge tone="gray">{inv.status}</BtBadge>
                        </td>
                        {tab === 'pending' ? (
                          <td className="text-center">
                            <div className="inline-flex gap-2">
                              <BtButton
                                size="sm"
                                disabled={actingId === inv.id}
                                onClick={() => void handleApprove(inv.id)}
                              >
                                Approve
                              </BtButton>
                              <BtButton
                                size="sm"
                                variant="secondary"
                                disabled={actingId === inv.id}
                                onClick={() => setRejectId(inv.id)}
                              >
                                Reject
                              </BtButton>
                            </div>
                          </td>
                        ) : tab === 'closed' ? (
                          <td className="text-center">
                            <BtButton
                              size="sm"
                              variant="secondary"
                              onClick={() => toggleLoanDetails(inv.id)}
                            >
                              {loanExpanded ? 'Hide loan' : 'View loan'}
                            </BtButton>
                          </td>
                        ) : null}
                      </tr>,
                    ];
                    if (tab === 'closed' && loanExpanded) {
                      rows.push(
                        <tr key={`${inv.id}-loan`}>
                          <td colSpan={6} className="bg-slate-50/60 px-5 py-4 align-top">
                            <InvoiceLinkedLoansPanel invoiceId={inv.id} defaultExpandedHistory />
                          </td>
                        </tr>,
                      );
                    }
                    return rows;
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </BtCard>

      {rejectId ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-lg">
            <h2 className="text-lg font-semibold text-slate-800">Reject invoice</h2>
            <p className="mt-1 text-sm text-slate-500">Optionally provide a reason for the borrower.</p>
            <textarea
              className={`${inputCls} mt-4 min-h-[80px]`}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Reason (optional)"
            />
            <div className="mt-4 flex justify-end gap-2">
              <BtButton variant="secondary" onClick={() => { setRejectId(null); setRejectReason(''); }}>
                Cancel
              </BtButton>
              <BtButton onClick={() => void handleReject()} disabled={actingId === rejectId}>
                Reject
              </BtButton>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
