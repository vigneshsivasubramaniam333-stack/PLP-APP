import { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  programApi,
  subProgramApi,
  invoiceApi,
  useAuth,
  notifyError,
  notifySuccess,
  BtPageHeader,
  BtCard,
  BtButton,
  flowTypeLabel,
  type InvoiceDiscountingFlowType,
} from '@plp/shared';
import type { Program, SubProgram } from '@plp/shared';

const inputCls = 'bt-input w-full';
const labelCls = 'bt-label';

type Props = {
  flowType: InvoiceDiscountingFlowType;
  backPath: string;
};

export default function SellerInitiatedInvoiceCreatePage({ flowType, backPath }: Props) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const borrowerId = useMemo(() => {
    if ((user?.linkedEntityType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
    return (user?.linkedEntityId ?? '').trim();
  }, [user?.linkedEntityType, user?.linkedEntityId]);

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [enrolledSubPrograms, setEnrolledSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    invoiceNumber: '',
    invoiceDate: '',
    dueDate: '',
    invoiceAmount: '',
    taxAmount: '0',
    poNumber: '',
    description: '',
  });

  useEffect(() => {
    Promise.all([
      programApi.list().then((r) => setPrograms(r.data.data || [])),
      subProgramApi.list().then((r) => setSubPrograms(r.data.data || [])),
    ]).catch(console.error);
  }, []);

  useEffect(() => {
    if (!borrowerId) {
      setEnrolledSubPrograms([]);
      return;
    }
    const rows = subPrograms.filter(
      (sp) =>
        sp.status === 'ACTIVE' &&
        (sp.flowType ?? '').trim() === flowType &&
        programs.some((p) => p.id === sp.programId && p.productType === 'INVOICE_DISCOUNTING'),
    );
    setEnrolledSubPrograms(rows);
    if (rows.length === 1) setSelectedSubProgramId(rows[0].id);
  }, [borrowerId, subPrograms, programs, flowType]);

  const selectedSub = enrolledSubPrograms.find((s) => s.id === selectedSubProgramId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!borrowerId || !selectedSub) {
      notifyError(null, 'Select a sub-program you are enrolled in.');
      return;
    }
    setSubmitting(true);
    try {
      await invoiceApi.create({
        invoiceNumber: form.invoiceNumber.trim(),
        borrowerId,
        anchorId: selectedSub.anchorId,
        programId: selectedSub.programId,
        subProgramId: selectedSub.id,
        flowType,
        invoiceDate: form.invoiceDate,
        dueDate: form.dueDate,
        invoiceAmount: Number(form.invoiceAmount),
        taxAmount: Number(form.taxAmount || 0),
        poNumber: form.poNumber || undefined,
        description: form.description || undefined,
      });
      notifySuccess('Submitted for anchor review');
      navigate(backPath);
    } catch (err) {
      notifyError(err, 'Could not submit invoice.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <BtPageHeader
        title={`Create — ${flowTypeLabel(flowType)}`}
        description="Your anchor will review and approve or reject this submission."
        actions={
          <Link to={backPath} className="text-sm font-semibold text-[var(--bt-orange)] hover:underline">
            ← Back to list
          </Link>
        }
      />

      <BtCard className="max-w-2xl p-6">
        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
          <div>
            <label className={labelCls}>Sub-program</label>
            <select
              className={inputCls}
              value={selectedSubProgramId}
              onChange={(e) => setSelectedSubProgramId(e.target.value)}
              required
            >
              <option value="">Select sub-program</option>
              {enrolledSubPrograms.map((sp) => (
                <option key={sp.id} value={sp.id}>
                  {sp.code} — {sp.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelCls}>Invoice / PO number</label>
            <input
              className={inputCls}
              value={form.invoiceNumber}
              onChange={(e) => setForm((f) => ({ ...f, invoiceNumber: e.target.value }))}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Invoice date</label>
              <input
                type="date"
                className={inputCls}
                value={form.invoiceDate}
                onChange={(e) => setForm((f) => ({ ...f, invoiceDate: e.target.value }))}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Due date</label>
              <input
                type="date"
                className={inputCls}
                value={form.dueDate}
                onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
                required
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Amount (INR)</label>
              <input
                type="number"
                min="0"
                step="0.01"
                className={inputCls}
                value={form.invoiceAmount}
                onChange={(e) => setForm((f) => ({ ...f, invoiceAmount: e.target.value }))}
                required
              />
            </div>
            <div>
              <label className={labelCls}>Tax (INR)</label>
              <input
                type="number"
                min="0"
                step="0.01"
                className={inputCls}
                value={form.taxAmount}
                onChange={(e) => setForm((f) => ({ ...f, taxAmount: e.target.value }))}
              />
            </div>
          </div>
          <div>
            <label className={labelCls}>PO reference (optional)</label>
            <input
              className={inputCls}
              value={form.poNumber}
              onChange={(e) => setForm((f) => ({ ...f, poNumber: e.target.value }))}
            />
          </div>
          <div>
            <label className={labelCls}>Description (optional)</label>
            <textarea
              className={`${inputCls} min-h-[72px]`}
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            />
          </div>
          <BtButton type="submit" disabled={submitting}>
            {submitting ? 'Submitting…' : 'Submit for anchor review'}
          </BtButton>
        </form>
      </BtCard>
    </div>
  );
}
