import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
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
  InvoiceListToolbar,
  InvoiceLinkedLoansPanel,
} from '@plp/shared';
import type { Program, Invoice, SubProgram, InvoiceListFilters, InvoicePageMeta } from '@plp/shared';
import {
  anchorIdFromUser,
  isInvoiceDiscountingSubProgramForFlow,
  isCheckerCannotConfirmOwnUpload,
  CONFIRM_SELF_UPLOAD_TOOLTIP,
  formatInvoiceCurrency,
  inputCls,
  labelCls,
} from '../invoice/invoiceShared';

export default function InvoicesPage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [pageMeta, setPageMeta] = useState<InvoicePageMeta | null>(null);
  const [listFilters, setListFilters] = useState<InvoiceListFilters>({
    search: '',
    status: '',
    lifecycle: 'active',
    page: 0,
    size: 20,
  });
  const [loading, setLoading] = useState(false);
  const [expandedLoanInvoiceIds, setExpandedLoanInvoiceIds] = useState<Set<string>>(() => new Set());

  const idSubPrograms = useMemo(() => {
    return subPrograms.filter((sp) =>
      isInvoiceDiscountingSubProgramForFlow(sp, programs, 'PURCHASE_BILL_DISCOUNTING'),
    );
  }, [subPrograms, programs]);

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
        search: listFilters.search || undefined,
        status: listFilters.status || undefined,
        lifecycle: listFilters.lifecycle,
        flowType: 'PURCHASE_BILL_DISCOUNTING',
        page: listFilters.page,
        size: listFilters.size,
      })
      .then((r) => {
        setInvoices(r.data.data || []);
        setPageMeta(r.data.page ?? null);
      })
      .catch(() => {
        setInvoices([]);
        setPageMeta(null);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (anchorId) loadInvoices();
  }, [anchorId, umbrellaProgramId, listFilters]);

  const handleVerify = async (id: string) => {
    try {
      await portalApi.anchorVerifyInvoice(id);
      loadInvoices();
    } catch (err) {
      console.error(err);
    }
  };

  const handleConfirm = async (id: string) => {
    try {
      await portalApi.anchorConfirmInvoice(id);
      loadInvoices();
    } catch (err) {
      console.error(err);
    }
  };

  const toggleLoanDetails = (invoiceId: string) => {
    setExpandedLoanInvoiceIds((prev) => {
      const next = new Set(prev);
      if (next.has(invoiceId)) next.delete(invoiceId);
      else next.add(invoiceId);
      return next;
    });
  };

  const showLoanDetails = listFilters.lifecycle === 'closed';

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Invoices</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <BtPageHeader
        title="Invoices"
        description="View, verify, and confirm invoices for your anchor"
        actions={
          <Link to="/invoices/create">
            <BtButton>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              Create invoice
            </BtButton>
          </Link>
        }
      />

      <BtCard className="mb-6 p-5">
        <div className="max-w-xl">
          <label className={labelCls}>Sub-program (invoice discounting)</label>
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

      <InvoiceListToolbar
        filters={listFilters}
        pageMeta={pageMeta}
        onChange={(next) => setListFilters((f) => ({ ...f, ...next }))}
      />

      <BtCard className="overflow-hidden p-0">
        <BtCardHeader
          title="Invoice list"
          actions={
            <span className="text-xs text-[var(--bt-gray-400)]">
              {pageMeta?.totalElements ?? invoices.length} records
            </span>
          }
        />

        {loading ? (
          <div className="px-5 py-16 text-center text-[var(--bt-gray-400)] text-sm animate-pulse">Loading invoices…</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="bt-table w-full">
              <thead>
                <tr>
                  <th className="min-w-[140px]">Invoice #</th>
                  <th className="min-w-[120px]">Dates</th>
                  <th className="text-right whitespace-nowrap">Amount</th>
                  <th className="text-right whitespace-nowrap">Net</th>
                  <th className="min-w-[100px] text-center">Copy</th>
                  <th className="text-center whitespace-nowrap">Status</th>
                  <th className="text-center whitespace-nowrap">Actions</th>
                </tr>
              </thead>
              <tbody>
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-5 py-16 text-center">
                      <div className="text-[var(--bt-gray-400)] text-sm">No invoices found</div>
                      <Link
                        to="/invoices/create"
                        className="inline-block mt-3 text-sm font-semibold text-[var(--bt-orange)] hover:underline"
                      >
                        Create your first invoice →
                      </Link>
                    </td>
                  </tr>
                ) : (
                  invoices.flatMap((inv) => {
                    const confirmBlocked = isCheckerCannotConfirmOwnUpload(inv, user);
                    const loanExpanded = expandedLoanInvoiceIds.has(inv.id);
                    const rows = [
                      <tr key={inv.id}>
                        <td>
                          <div className="font-mono text-xs font-medium text-[var(--bt-gray-800)]">{inv.invoiceNumber}</div>
                          {inv.poNumber ? <div className="text-[11px] text-[var(--bt-gray-400)] mt-0.5">PO: {inv.poNumber}</div> : null}
                        </td>
                        <td>
                          <div className="text-xs text-[var(--bt-gray-600)]">{inv.invoiceDate}</div>
                          <div className="text-[11px] text-[var(--bt-gray-400)] mt-0.5">Due: {inv.dueDate}</div>
                        </td>
                        <td className="text-right tabular-nums whitespace-nowrap text-[var(--bt-gray-700)]">{formatInvoiceCurrency(inv.invoiceAmount)}</td>
                        <td className="text-right tabular-nums whitespace-nowrap font-medium text-[var(--bt-gray-900)]">{formatInvoiceCurrency(inv.netAmount)}</td>
                        <td className="text-center whitespace-nowrap">
                          <DigitalInvoiceAttachment invoiceId={inv.id} fileName={inv.digitalInvoiceFileName} />
                        </td>
                        <td className="text-center whitespace-nowrap">
                          <BtBadge tone="gray">{inv.status}</BtBadge>
                        </td>
                        <td className="text-center whitespace-nowrap">
                          <div className="inline-flex items-center justify-center gap-2">
                            {inv.status === 'UPLOADED' ? (
                              <button
                                type="button"
                                onClick={() => void handleVerify(inv.id)}
                                className="bt-btn bt-btn-secondary bt-btn-sm"
                              >
                                Verify
                              </button>
                            ) : null}
                            {inv.status === 'VERIFIED' ? (
                              <span title={confirmBlocked ? CONFIRM_SELF_UPLOAD_TOOLTIP : undefined}>
                                <button
                                  type="button"
                                  onClick={() => void handleConfirm(inv.id)}
                                  disabled={confirmBlocked}
                                  className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
                                >
                                  Confirm
                                </button>
                              </span>
                            ) : null}
                            {showLoanDetails ? (
                              <button
                                type="button"
                                onClick={() => toggleLoanDetails(inv.id)}
                                className="bt-btn bt-btn-secondary bt-btn-sm"
                              >
                                {loanExpanded ? 'Hide loan' : 'View loan'}
                              </button>
                            ) : null}
                            {!['UPLOADED', 'VERIFIED'].includes(inv.status) && !showLoanDetails ? (
                              <span className="text-xs text-[var(--bt-gray-400)]">—</span>
                            ) : null}
                          </div>
                        </td>
                      </tr>,
                    ];

                    if (showLoanDetails && loanExpanded) {
                      rows.push(
                        <tr key={`${inv.id}-loan`}>
                          <td colSpan={7} className="bg-slate-50/60 px-5 py-4 align-top">
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
    </div>
  );
}
