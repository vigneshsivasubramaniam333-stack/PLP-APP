import { useState, useEffect, useRef, useMemo } from 'react';
import axios from 'axios';
import {
  borrowerApi,
  programApi,
  portalApi,
  subProgramApi,
  useAuth,
  openDigitalInvoiceDownload,
  extractApiErrorMessage,
} from '@plp/shared';
import type { Program, Invoice, AuthUser, Borrower, SubProgram } from '@plp/shared';

const NO_LINKED_BORROWERS =
  'No linked borrowers for this sub-program. Ask your lender to add the counterparty borrower to this sub-program.';

const CONFIRM_SELF_UPLOAD_TOOLTIP = 'You cannot approve an invoice uploaded by you';

function parseRoleList(role: string): string[] {
  return role
    .split(',')
    .map((r) => r.trim())
    .filter(Boolean);
}

/** Checker cannot confirm own upload; admin bypass; legacy invoices (no uploader) allowed. */
function isCheckerCannotConfirmOwnUpload(invoice: Invoice, user: AuthUser | null): boolean {
  if (!user) return false;
  const roles = parseRoleList(user.role);
  if (roles.includes('ANCHOR_ADMIN')) return false;
  if (!roles.includes('ANCHOR_CHECKER')) return false;
  const uid = invoice.uploadedByUserId;
  if (uid == null || uid === '') return false;
  return uid === user.userId;
}

const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

function anchorIdFromUser(linkedType: string | null | undefined, linkedId: string | null | undefined): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'ANCHOR') return '';
  return (linkedId ?? '').trim();
}

function isInvoiceDiscountingSubProgram(sp: SubProgram, programs: Program[]): boolean {
  const parent = programs.find((p) => p.id === sp.programId);
  return parent?.productType === 'INVOICE_DISCOUNTING';
}

export default function InvoiceUploadPage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [uploadResult, setUploadResult] = useState<{ rows: number; error?: string } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [tab, setTab] = useState<'upload' | 'manual' | 'view'>('upload');
  const fileRef = useRef<HTMLInputElement>(null);
  const digitalInvoiceFileRef = useRef<HTMLInputElement>(null);

  const [manual, setManual] = useState({
    invoiceNumber: '',
    borrowerId: '',
    invoiceDate: '',
    dueDate: '',
    invoiceAmount: '',
    taxAmount: '0',
    poNumber: '',
    grnNumber: '',
    gstinSeller: '',
    gstinBuyer: '',
    paymentTerms: '',
    description: '',
  });
  const [manualMsg, setManualMsg] = useState('');
  const [borrowersPick, setBorrowersPick] = useState<Borrower[]>([]);

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
    if (!file || !anchorId || !umbrellaProgramId) return;
    setUploading(true);
    setUploadResult(null);
    try {
      const res = await portalApi.anchorInvoiceUpload(anchorId, umbrellaProgramId, file);
      setUploadResult({ rows: res.data.data.rowsProcessed });
      loadInvoices();
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
      const createRes = await portalApi.anchorCreateInvoice({
        invoiceNumber: manual.invoiceNumber,
        borrowerId: manual.borrowerId,
        anchorId,
        programId: umbrellaProgramId,
        invoiceDate: manual.invoiceDate,
        dueDate: manual.dueDate,
        invoiceAmount: parseFloat(manual.invoiceAmount),
        taxAmount: parseFloat(manual.taxAmount || '0'),
        netAmount: parseFloat(manual.invoiceAmount) + parseFloat(manual.taxAmount || '0'),
        poNumber: manual.poNumber || null,
        grnNumber: manual.grnNumber || null,
        gstinSeller: manual.gstinSeller || null,
        gstinBuyer: manual.gstinBuyer || null,
        paymentTerms: manual.paymentTerms || null,
        description: manual.description || null,
        source: 'MANUAL',
      });
      const created = createRes.data?.data as Invoice | undefined;
      const digitalFile = digitalInvoiceFileRef.current?.files?.[0];
      let msg = 'Invoice entry saved successfully';
      if (created?.id && digitalFile) {
        try {
          const up = await portalApi.anchorUploadDigitalInvoice(created.id, digitalFile);
          const attachment = up.data?.attachment as { storageMode?: string; todo?: string } | undefined;
          if (attachment?.todo) {
            msg += `. ${attachment.todo}`;
          } else if (attachment?.storageMode === 'OBJECT_STORAGE') {
            msg += '. Digital invoice stored in object storage.';
          }
        } catch (attachErr: unknown) {
          let attachDetail = 'Digital invoice upload failed';
          if (axios.isAxiosError(attachErr)) {
            const body = attachErr.response?.data as { message?: string } | undefined;
            attachDetail = body?.message ?? attachErr.message;
          } else if (attachErr instanceof Error) {
            attachDetail = attachErr.message;
          }
          msg += `. Warning: ${attachDetail}`;
        }
      }
      setManualMsg(msg);
      setManual({
        invoiceNumber: '',
        borrowerId: '',
        invoiceDate: '',
        dueDate: '',
        invoiceAmount: '',
        taxAmount: '0',
        poNumber: '',
        grnNumber: '',
        gstinSeller: '',
        gstinBuyer: '',
        paymentTerms: '',
        description: '',
      });
      if (digitalInvoiceFileRef.current) digitalInvoiceFileRef.current.value = '';
      loadInvoices();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Save failed';
      setManualMsg('Error: ' + message);
    }
  };

  const loadInvoices = () => {
    if (!anchorId) return;
    portalApi
      .anchorInvoices(anchorId, umbrellaProgramId || undefined)
      .then((r) => setInvoices(r.data.data || []))
      .catch(console.error);
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

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount || 0);

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Invoice Upload &amp; Management</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Invoice Upload &amp; Management</h1>
        <p className="text-sm text-slate-500 mt-1">
          Scoped to your anchor · Invoice discounting sub-programs · Borrowers filtered by selected sub-program
        </p>
      </div>

      {/* Context selectors */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-1 gap-4 max-w-xl">
          <div>
            <label className={labelCls}>Sub-program (invoice discounting)</label>
            <select
              value={selectedSubProgramId}
              onChange={(e) => setSelectedSubProgramId(e.target.value)}
              className={inputCls}
            >
              <option value="">Select sub-program</option>
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
            {idSubPrograms.length === 0 && (
              <p className="text-xs text-amber-700 mt-1.5">
                No active invoice-discounting sub-programs for your anchor.
              </p>
            )}
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
            {t === 'upload' ? 'CSV upload' : t === 'manual' ? 'Manual entry' : 'View invoices'}
          </button>
        ))}
      </div>

      {/* CSV Upload Tab */}
      {tab === 'upload' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-slate-700 mb-3">Upload invoice CSV</h3>
          <p className="text-xs text-slate-500 mb-4">
            Format:{' '}
            <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">
              invoiceNumber, borrowerCode, invoiceDate (yyyy-MM-dd), dueDate, invoiceAmount, taxAmount
            </code>
          </p>
          <div className="flex items-center gap-4">
            <input type="file" ref={fileRef} accept=".csv" className="text-sm text-slate-600" />
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
                uploadResult.error ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              }`}
            >
              {uploadResult.error || `${uploadResult.rows} invoice(s) processed successfully`}
            </div>
          )}
        </div>
      )}

      {/* Manual Entry Tab */}
      {tab === 'manual' && (
        <form onSubmit={handleManualSubmit} className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-slate-700 mb-4">Manual invoice entry</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Invoice number *</label>
              <input
                type="text"
                value={manual.invoiceNumber}
                onChange={(e) => setManual({ ...manual, invoiceNumber: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Borrower (invoice counterparty) *</label>
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
                <p className="text-xs text-slate-500 mt-1">Choose a sub-program first.</p>
              ) : borrowersPick.length === 0 ? (
                <p className="text-xs text-amber-700 mt-1.5">{NO_LINKED_BORROWERS}</p>
              ) : null}
            </div>
            <div>
              <label className={labelCls}>Invoice date *</label>
              <input
                type="date"
                value={manual.invoiceDate}
                onChange={(e) => setManual({ ...manual, invoiceDate: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Due date *</label>
              <input
                type="date"
                value={manual.dueDate}
                onChange={(e) => setManual({ ...manual, dueDate: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Invoice amount *</label>
              <input
                type="number"
                step="0.01"
                value={manual.invoiceAmount}
                onChange={(e) => setManual({ ...manual, invoiceAmount: e.target.value })}
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Tax amount (GST)</label>
              <input
                type="number"
                step="0.01"
                value={manual.taxAmount}
                onChange={(e) => setManual({ ...manual, taxAmount: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>PO number</label>
              <input
                type="text"
                value={manual.poNumber}
                onChange={(e) => setManual({ ...manual, poNumber: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>GRN number</label>
              <input
                type="text"
                value={manual.grnNumber}
                onChange={(e) => setManual({ ...manual, grnNumber: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>GSTIN (seller)</label>
              <input
                type="text"
                value={manual.gstinSeller}
                onChange={(e) => setManual({ ...manual, gstinSeller: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>GSTIN (buyer)</label>
              <input
                type="text"
                value={manual.gstinBuyer}
                onChange={(e) => setManual({ ...manual, gstinBuyer: e.target.value })}
                className={inputCls}
              />
            </div>
            <div className="md:col-span-2">
              <label className={labelCls}>Digital invoice (optional)</label>
              <input ref={digitalInvoiceFileRef} type="file" className="text-sm text-slate-600" />
              <p className="text-xs text-slate-500 mt-1">
                Uploaded after the invoice row is created. Requires MinIO (<code className="bg-slate-100 px-1 rounded">plp.storage.minio</code>)
                for bytes; otherwise metadata only with a TODO in the success message.
              </p>
            </div>
          </div>
          <div className="mt-4 flex items-center gap-4">
            <button
              type="submit"
              disabled={!umbrellaProgramId || borrowersPick.length === 0 || !manual.borrowerId}
              className="px-5 py-2.5 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 disabled:opacity-50"
            >
              Save invoice
            </button>
          </div>
          {manualMsg && (
            <div
              className={`mt-4 p-4 rounded-lg text-sm ${
                manualMsg.startsWith('Error') ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              }`}
            >
              {manualMsg}
            </div>
          )}
        </form>
      )}

      {/* View Invoices Tab */}
      {tab === 'view' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center">
            <h3 className="text-sm font-semibold text-slate-700">Invoices</h3>
            <span className="text-xs text-slate-400">{invoices.length} records</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Invoice #</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Dates</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Amount</th>
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Net</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Digital</th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                  <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-5 py-12 text-center text-slate-400 text-sm">
                      No invoices found
                    </td>
                  </tr>
                ) : (
                  invoices.map((inv) => {
                    const confirmBlocked = isCheckerCannotConfirmOwnUpload(inv, user);
                    return (
                      <tr key={inv.id} className="hover:bg-slate-50/80">
                        <td className="px-5 py-3">
                          <div className="font-mono text-xs font-medium text-slate-700">{inv.invoiceNumber}</div>
                          {inv.poNumber && <div className="text-[11px] text-slate-400 mt-0.5">PO: {inv.poNumber}</div>}
                        </td>
                        <td className="px-5 py-3">
                          <div className="text-xs text-slate-600">{inv.invoiceDate}</div>
                          <div className="text-[11px] text-slate-400 mt-0.5">Due: {inv.dueDate}</div>
                        </td>
                        <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(inv.invoiceAmount)}</td>
                        <td className="px-5 py-3 text-right font-medium text-slate-800">{formatCurrency(inv.netAmount)}</td>
                        <td className="px-5 py-3 text-xs text-slate-600 max-w-[200px]">
                          {inv.digitalInvoiceFileName ? (
                            <div className="flex flex-col gap-1">
                              <span className="font-mono text-[11px] break-all">{inv.digitalInvoiceFileName}</span>
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
                            </div>
                          ) : (
                            <span className="text-slate-400">—</span>
                          )}
                        </td>
                        <td className="px-5 py-3 text-center">
                          <InvoiceBadge status={inv.status} />
                        </td>
                        <td className="px-5 py-3 text-center">
                          <div className="flex items-center justify-center gap-2">
                            {inv.status === 'UPLOADED' && (
                              <button
                                onClick={() => void handleVerify(inv.id)}
                                className="px-2.5 py-1 text-xs font-semibold text-blue-600 bg-blue-50 rounded hover:bg-blue-100"
                              >
                                Verify
                              </button>
                            )}
                            {inv.status === 'VERIFIED' && (
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
                                {confirmBlocked && (
                                  <span className="text-[10px] text-amber-700 max-w-[160px] leading-tight text-center px-1">
                                    {CONFIRM_SELF_UPLOAD_TOOLTIP}
                                  </span>
                                )}
                              </div>
                            )}
                            {!['UPLOADED', 'VERIFIED'].includes(inv.status) && <span className="text-xs text-slate-400">—</span>}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function InvoiceBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    UPLOADED: 'bg-slate-50 text-slate-600 ring-1 ring-slate-400/20',
    VERIFIED: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
    ELIGIBLE: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    PARTIALLY_DISCOUNTED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    FULLY_DISCOUNTED: 'bg-purple-50 text-purple-700 ring-1 ring-purple-600/20',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${styles[status] || 'bg-slate-50 text-slate-600'}`}>
      {status}
    </span>
  );
}
