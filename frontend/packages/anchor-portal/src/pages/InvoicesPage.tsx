import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  programApi,
  portalApi,
  subProgramApi,
  useAuth,
  openDigitalInvoiceDownload,
  extractApiErrorMessage,
} from '@plp/shared';
import type { Program, Invoice, SubProgram } from '@plp/shared';
import {
  anchorIdFromUser,
  isInvoiceDiscountingSubProgram,
  isCheckerCannotConfirmOwnUpload,
  CONFIRM_SELF_UPLOAD_TOOLTIP,
  formatInvoiceCurrency,
  invoiceStatusBadgeClass,
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
  const [loading, setLoading] = useState(false);

  const idSubPrograms = useMemo(() => {
    return subPrograms.filter((sp) => isInvoiceDiscountingSubProgram(sp, programs) && sp.status === 'ACTIVE');
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
      .anchorInvoices(anchorId, umbrellaProgramId || undefined)
      .then((r) => setInvoices(r.data.data || []))
      .catch(() => setInvoices([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (anchorId) loadInvoices();
  }, [anchorId, umbrellaProgramId]);

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
      <div className="mb-6 flex flex-wrap justify-between items-start gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Invoices</h1>
          <p className="text-sm text-slate-500 mt-1">View, verify, and confirm invoices for your anchor</p>
        </div>
        <Link
          to="/invoices/create"
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 shadow-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Create invoice
        </Link>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
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
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center">
          <h3 className="text-sm font-semibold text-slate-700">Invoice list</h3>
          <span className="text-xs text-slate-400">{invoices.length} records</span>
        </div>

        {loading ? (
          <div className="px-5 py-16 text-center text-slate-400 text-sm animate-pulse">Loading invoices…</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Invoice #
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Dates
                  </th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Amount
                  </th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Net
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Digital
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-5 py-16 text-center">
                      <div className="text-slate-400 text-sm">No invoices found</div>
                      <Link
                        to="/invoices/create"
                        className="inline-block mt-3 text-sm font-semibold text-emerald-600 hover:text-emerald-700"
                      >
                        Create your first invoice →
                      </Link>
                    </td>
                  </tr>
                ) : (
                  invoices.map((inv) => {
                    const confirmBlocked = isCheckerCannotConfirmOwnUpload(inv, user);
                    return (
                      <tr key={inv.id} className="hover:bg-slate-50/80">
                        <td className="px-5 py-3">
                          <div className="font-mono text-xs font-medium text-slate-700">{inv.invoiceNumber}</div>
                          {inv.poNumber ? <div className="text-[11px] text-slate-400 mt-0.5">PO: {inv.poNumber}</div> : null}
                        </td>
                        <td className="px-5 py-3">
                          <div className="text-xs text-slate-600">{inv.invoiceDate}</div>
                          <div className="text-[11px] text-slate-400 mt-0.5">Due: {inv.dueDate}</div>
                        </td>
                        <td className="px-5 py-3 text-right text-slate-700">{formatInvoiceCurrency(inv.invoiceAmount)}</td>
                        <td className="px-5 py-3 text-right font-medium text-slate-800">{formatInvoiceCurrency(inv.netAmount)}</td>
                        <td className="px-5 py-3 text-xs text-slate-600 max-w-[200px]">
                          {inv.digitalInvoiceFileName ? (
                            <button
                              type="button"
                              onClick={() => {
                                void openDigitalInvoiceDownload(inv.id).catch((e: unknown) => {
                                  window.alert(extractApiErrorMessage(e, 'Could not open digital invoice'));
                                });
                              }}
                              className="text-left text-xs font-semibold text-emerald-700 hover:text-emerald-900 underline"
                            >
                              View / Download
                            </button>
                          ) : (
                            <span className="text-slate-400">—</span>
                          )}
                        </td>
                        <td className="px-5 py-3 text-center">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${invoiceStatusBadgeClass(inv.status)}`}
                          >
                            {inv.status}
                          </span>
                        </td>
                        <td className="px-5 py-3 text-center">
                          <div className="flex items-center justify-center gap-2">
                            {inv.status === 'UPLOADED' ? (
                              <button
                                onClick={() => void handleVerify(inv.id)}
                                className="px-2.5 py-1 text-xs font-semibold text-blue-600 bg-blue-50 rounded hover:bg-blue-100"
                              >
                                Verify
                              </button>
                            ) : null}
                            {inv.status === 'VERIFIED' ? (
                              <div className="flex flex-col items-center gap-1">
                                <span title={confirmBlocked ? CONFIRM_SELF_UPLOAD_TOOLTIP : undefined}>
                                  <button
                                    type="button"
                                    onClick={() => void handleConfirm(inv.id)}
                                    disabled={confirmBlocked}
                                    className={`px-2.5 py-1 text-xs font-semibold rounded ${
                                      confirmBlocked
                                        ? 'text-emerald-400 bg-emerald-50/80 cursor-not-allowed opacity-70'
                                        : 'text-emerald-600 bg-emerald-50 hover:bg-emerald-100'
                                    }`}
                                  >
                                    Confirm
                                  </button>
                                </span>
                              </div>
                            ) : null}
                            {!['UPLOADED', 'VERIFIED'].includes(inv.status) ? (
                              <span className="text-xs text-slate-400">—</span>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
