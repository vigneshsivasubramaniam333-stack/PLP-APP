import { useState, useEffect, useRef, useMemo } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import {
  borrowerApi,
  programApi,
  portalApi,
  subProgramApi,
  useAuth,
  BtPageHeader,
  BtCard,
  BtButton,
  extractApiErrorMessage,
  invoiceDueDateError,
  sanitizeNonNegativeNumberInput,
} from '@plp/shared';
import type { Program, Invoice, Borrower, SubProgram } from '@plp/shared';
import {
  anchorIdFromUser,
  isInvoiceDiscountingSubProgramForFlow,
  NO_LINKED_BORROWERS,
  downloadSampleInvoiceCsv,
  inputCls,
  labelCls,
} from '../invoice/invoiceShared';

export default function InvoiceCreatePage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [mode, setMode] = useState<'upload' | 'manual'>('upload');
  const [uploadResult, setUploadResult] = useState<{ rows: number; skipped?: number; error?: string; errors?: { row: number; reason: string }[] } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [csvFile, setCsvFile] = useState<File | null>(null);
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
  const [createAsApproved, setCreateAsApproved] = useState(false);
  const [borrowersPick, setBorrowersPick] = useState<Borrower[]>([]);

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
    const file = csvFile ?? fileRef.current?.files?.[0];
    if (!file || !anchorId || !umbrellaProgramId || !selectedSubProgramId) return;
    setUploading(true);
    setUploadResult(null);
    try {
      const res = await portalApi.anchorInvoiceUpload(anchorId, umbrellaProgramId, file, selectedSubProgramId);
      const data = res.data?.data as { rowsProcessed?: number; rowsSkipped?: number; errors?: { row: number; reason: string }[] } | undefined;
      setUploadResult({
        rows: data?.rowsProcessed ?? 0,
        skipped: data?.rowsSkipped,
        errors: data?.errors,
      });
      if (fileRef.current) fileRef.current.value = '';
      setCsvFile(null);
    } catch (err: unknown) {
      setUploadResult({ rows: 0, error: extractApiErrorMessage(err, 'Upload failed') });
    } finally {
      setUploading(false);
    }
  };

  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setManualMsg('');
    if (!anchorId || !umbrellaProgramId || !selectedSubProgramId) return;
    const dateErr = invoiceDueDateError(manual.invoiceDate, manual.dueDate);
    if (dateErr) {
      setManualMsg('Error: ' + dateErr);
      return;
    }
    try {
      const createRes = await portalApi.anchorCreateInvoice({
        invoiceNumber: manual.invoiceNumber,
        borrowerId: manual.borrowerId,
        anchorId,
        programId: umbrellaProgramId,
        subProgramId: selectedSubProgramId,
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
        createAsApproved,
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
          } else if (attachment?.storageMode === 'OBJECT_STORAGE' || attachment?.storageMode === 'LOCAL_FILESYSTEM') {
            msg += '. Digital invoice stored successfully.';
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
    } catch (err: unknown) {
      setManualMsg('Error: ' + extractApiErrorMessage(err, 'Save failed'));
    }
  };

  if (!anchorId) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Create invoice</h1>
        <p className="mt-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <BtPageHeader
        title="Create invoice"
        description="Upload a CSV batch or enter a single invoice manually"
        breadcrumb={
          <Link to="/invoices" className="text-sm font-medium text-[var(--bt-orange)] hover:underline">
            ← Back to invoices
          </Link>
        }
      />

      <BtCard className="mb-6 p-5">
        <div className="max-w-xl">
          <label className={labelCls}>Sub-program (invoice discounting) *</label>
          <select
            value={selectedSubProgramId}
            onChange={(e) => setSelectedSubProgramId(e.target.value)}
            className={inputCls}
            required
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
          {idSubPrograms.length === 0 ? (
            <p className="text-xs text-amber-700 mt-1.5">No active invoice-discounting sub-programs for your anchor.</p>
          ) : null}
        </div>
      </BtCard>

      <div className="bt-tabs">
        {(['upload', 'manual'] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setMode(t)}
            className={`bt-tab${mode === t ? ' active' : ''}`}
          >
            {t === 'upload' ? 'CSV upload' : 'Manual entry'}
          </button>
        ))}
      </div>

      {mode === 'upload' ? (
        <BtCard className="p-6">
          <h3 className="text-sm font-semibold text-[var(--bt-gray-800)] mb-3">Upload invoice CSV</h3>
          <p className="text-xs text-slate-500 mb-4">
            Format:{' '}
            <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">
              invoiceNumber, partyCode (or borrowerCode), invoiceDate, dueDate, invoiceAmount, taxAmount
            </code>
            . Use <strong>subProgramCode</strong> column when uploading with party codes.
          </p>
          <div className="flex flex-wrap items-center gap-4">
            <input
              type="file"
              ref={fileRef}
              accept=".csv"
              className="text-sm text-slate-600"
              onChange={(e) => setCsvFile(e.target.files?.[0] ?? null)}
            />
            <button
              type="button"
              onClick={() => void handleUpload()}
              disabled={uploading || !selectedSubProgramId || !csvFile}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              {uploading ? 'Uploading...' : 'Upload CSV'}
            </button>
            <button
              type="button"
              onClick={downloadSampleInvoiceCsv}
              className="bt-btn bt-btn-secondary"
            >
              Download sample CSV
            </button>
          </div>
          {!selectedSubProgramId ? (
            <p className="mt-2 text-xs text-amber-700">Select a sub-program above before uploading.</p>
          ) : !csvFile ? (
            <p className="mt-2 text-xs text-slate-500">Choose a CSV file to enable upload.</p>
          ) : null}
          {uploadResult ? (
            <div
              className={`mt-4 p-4 rounded-lg text-sm bt-alert ${
                uploadResult.error
                  ? 'bt-alert-error'
                  : uploadResult.rows === 0 && (uploadResult.errors?.length ?? 0) > 0
                    ? 'bt-alert-warning'
                    : 'bt-alert-success'
              }`}
            >
              {uploadResult.error || (
                <>
                  {uploadResult.rows} invoice(s) processed successfully.
                  {uploadResult.skipped ? ` ${uploadResult.skipped} row(s) skipped.` : ''}
                  {uploadResult.errors && uploadResult.errors.length > 0 ? (
                    <ul className="mt-2 text-left list-disc pl-5">
                      {uploadResult.errors.slice(0, 5).map((e) => (
                        <li key={e.row}>Row {e.row}: {e.reason}</li>
                      ))}
                    </ul>
                  ) : null}{' '}
                  <Link to="/invoices" className="font-semibold underline">
                    View invoice list
                  </Link>
                </>
              )}
            </div>
          ) : null}
        </BtCard>
      ) : (
        <BtCard className="p-6">
          <form onSubmit={handleManualSubmit}>
          <h3 className="text-sm font-semibold text-[var(--bt-gray-800)] mb-4">Manual invoice entry</h3>
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
                min={manual.invoiceDate || undefined}
                onChange={(e) => setManual({ ...manual, dueDate: e.target.value })}
                className={inputCls}
                required
              />
              {invoiceDueDateError(manual.invoiceDate, manual.dueDate) ? (
                <p className="text-xs text-rose-700 mt-1">
                  {invoiceDueDateError(manual.invoiceDate, manual.dueDate)}
                </p>
              ) : null}
            </div>
            <div>
              <label className={labelCls}>Invoice amount *</label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={manual.invoiceAmount}
                onChange={(e) =>
                  setManual({ ...manual, invoiceAmount: sanitizeNonNegativeNumberInput(e.target.value) })
                }
                className={inputCls}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Tax amount (GST)</label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={manual.taxAmount}
                onChange={(e) =>
                  setManual({ ...manual, taxAmount: sanitizeNonNegativeNumberInput(e.target.value) })
                }
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
              <div className="mt-1 rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center hover:border-[var(--bt-orange)]/50 transition-colors">
                <input
                  ref={digitalInvoiceFileRef}
                  type="file"
                  accept=".pdf,image/*"
                  className="mx-auto block text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-[var(--bt-orange)] file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:opacity-90"
                />
                <p className="mt-2 text-xs text-slate-500">PDF or image, max 10 MB. Stored locally or in object storage.</p>
              </div>
            </div>
          </div>
          <label className="mt-4 flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
            <input
              type="checkbox"
              checked={createAsApproved}
              onChange={(e) => setCreateAsApproved(e.target.checked)}
              className="rounded border-slate-300"
            />
            Create as approved (skip verify and confirm)
          </label>
          <div className="mt-4 flex flex-wrap items-center gap-4">
            <BtButton
              type="submit"
              disabled={
                !umbrellaProgramId ||
                borrowersPick.length === 0 ||
                !manual.borrowerId ||
                !!invoiceDueDateError(manual.invoiceDate, manual.dueDate)
              }
            >
              Save invoice
            </BtButton>
            <Link to="/invoices" className="text-sm font-medium text-[var(--bt-gray-500)] hover:text-[var(--bt-gray-700)]">
              Cancel
            </Link>
          </div>
          {manualMsg ? (
            <div
              className={`mt-4 p-4 rounded-lg text-sm bt-alert ${manualMsg.startsWith('Error') ? 'bt-alert-error' : 'bt-alert-success'}`}
            >
              {manualMsg}
              {!manualMsg.startsWith('Error') ? (
                <>
                  {' '}
                  <Link to="/invoices" className="font-semibold underline">
                    View invoice list
                  </Link>
                </>
              ) : null}
            </div>
          ) : null}
          </form>
        </BtCard>
      )}
    </div>
  );
}
