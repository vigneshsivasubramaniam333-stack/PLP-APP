import { useEffect, useState } from 'react';
import {
  earlyPayApi,
  notifyError,
  notifySuccess,
  type Invoice,
} from '@plp/shared';

type EarlyPayParameter = {
  id: string;
  discountPercentage: number;
  totalMarginAmount: number;
  consumedMarginAmount: number;
  unAllocatedAmount: number;
};

type Props = {
  invoice: Invoice;
  onSuccess: () => void;
  onClose: () => void;
};

export function EarlyPayRequestModal({ invoice, onSuccess, onClose }: Props) {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [valid, setValid] = useState(false);
  const [validationMsg, setValidationMsg] = useState('');
  const [param, setParam] = useState<EarlyPayParameter | null>(null);
  const [requestedAmount, setRequestedAmount] = useState(0);
  const [discountAmount, setDiscountAmount] = useState(0);

  const balDue = invoice.balDueAmount ?? invoice.netAmount ?? 0;

  useEffect(() => {
    void loadParameter();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invoice.id, invoice.subProgramId]);

  async function loadParameter() {
    if (!invoice.subProgramId) {
      setValidationMsg('Invoice is not linked to a sub-program.');
      setValid(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await earlyPayApi.todayParameter(invoice.subProgramId);
      const data = res.data?.data as EarlyPayParameter | undefined;
      if (!data?.id) {
        setValidationMsg('No Early Pay parameter available for today.');
        setValid(false);
        return;
      }
      const pct = Number(data.discountPercentage) / 100;
      const disc = balDue * pct;
      const requested = balDue - disc;
      const available =
        Number(data.totalMarginAmount ?? 0) - Number(data.consumedMarginAmount ?? 0);
      setParam(data);
      setDiscountAmount(disc);
      setRequestedAmount(requested);
      if (requested > available) {
        setValidationMsg("The requested amount exceeds the anchor's available Early Pay limit for today.");
        setValid(false);
      } else {
        setValid(true);
      }
    } catch (err) {
      notifyError(err, 'Failed to load Early Pay parameters');
      setValidationMsg('An error occurred while loading Early Pay parameters.');
      setValid(false);
    } finally {
      setLoading(false);
    }
  }

  async function submit() {
    if (!param || !valid) return;
    setSubmitting(true);
    try {
      await earlyPayApi.createRequest({
        invoiceId: invoice.id,
        epParameterId: param.id,
        requestedAmount,
      });
      notifySuccess('Early Pay request submitted');
      onSuccess();
      onClose();
    } catch (err) {
      notifyError(err, 'Early Pay request failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white shadow-lg border border-slate-200">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="text-base font-semibold text-slate-800">Request Early Pay</h2>
          <p className="text-xs text-slate-500 mt-1">Invoice {invoice.invoiceNumber}</p>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          {loading ? (
            <p className="text-slate-500">Loading parameters…</p>
          ) : (
            <>
              <div className="flex justify-between">
                <span className="text-slate-600">Balance due</span>
                <span className="font-medium tabular-nums">{balDue.toFixed(2)}</span>
              </div>
              {param && (
                <>
                  <div className="flex justify-between">
                    <span className="text-slate-600">Discount %</span>
                    <span className="font-medium">{param.discountPercentage}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-600">Discount amount</span>
                    <span className="font-medium tabular-nums">{discountAmount.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-600">Requested amount</span>
                    <span className="font-semibold tabular-nums text-sky-800">{requestedAmount.toFixed(2)}</span>
                  </div>
                </>
              )}
              {!valid && validationMsg && (
                <p className="text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-xs">
                  {validationMsg}
                </p>
              )}
            </>
          )}
        </div>
        <div className="px-5 py-4 border-t border-slate-100 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary bt-btn-sm">
            Cancel
          </button>
          <button
            type="button"
            disabled={!valid || submitting || loading}
            onClick={() => void submit()}
            className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit request'}
          </button>
        </div>
      </div>
    </div>
  );
}
