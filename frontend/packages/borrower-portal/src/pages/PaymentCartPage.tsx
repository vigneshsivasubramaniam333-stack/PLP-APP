import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  paymentCartApi,
  payuApi,
  useAuth,
  notifyError,
  type PaymentCheckoutLine,
} from '@plp/shared';

export default function PaymentCartPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const borrowerId = useMemo(() => {
    if ((user?.linkedEntityType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
    return (user?.linkedEntityId ?? '').trim();
  }, [user?.linkedEntityType, user?.linkedEntityId]);

  const [lines, setLines] = useState<PaymentCheckoutLine[]>([]);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);

  const load = useCallback(async () => {
    if (!borrowerId) return;
    setLoading(true);
    try {
      const res = await paymentCartApi.listLines(borrowerId);
      setLines(res.data?.data ?? []);
    } catch (err) {
      notifyError(err, 'Could not load payment cart');
    } finally {
      setLoading(false);
    }
  }, [borrowerId]);

  useEffect(() => {
    void load();
  }, [load]);

  const total = lines.reduce((sum, l) => sum + (l.amountToPay || 0), 0);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
      amount || 0,
    );

  const removeLine = async (lineId: string) => {
    try {
      await paymentCartApi.removeLine(lineId, borrowerId);
      await load();
      window.dispatchEvent(new Event('plp-payment-cart-changed'));
    } catch (err) {
      notifyError(err, 'Could not remove item');
    }
  };

  const payViaPayu = async () => {
    setPaying(true);
    try {
      const res = await payuApi.initiate('PLP', borrowerId);
      const payload = res.data?.data;
      if (!payload) {
        throw new Error('PayU initiation failed');
      }
      navigate('/payments/payu', { state: { payu: payload } });
    } catch (err) {
      notifyError(err, 'Could not start PayU payment');
    } finally {
      setPaying(false);
    }
  };

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Payment cart</h1>
          <p className="text-sm text-slate-500 mt-1">
            Review selected invoices and pay via PayU. Funds are settled to your loans after admin reconciliation (PRUS).
          </p>
        </div>
        <Link to="/invoice-discounting" className="text-sm font-semibold text-sky-700 hover:underline">
          ← Back to invoices
        </Link>
      </div>

      {loading ? (
        <p className="text-sm text-slate-400">Loading cart…</p>
      ) : lines.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 p-8 text-center text-sm text-slate-500">
          Your cart is empty. Add financed invoices from{' '}
          <Link to="/invoice-discounting" className="text-sky-700 font-semibold hover:underline">
            Invoice Discounting
          </Link>
          .
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Invoice #</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase">Amount</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {lines.map((line) => (
                <tr key={line.id}>
                  <td className="px-5 py-3 font-mono text-xs">{line.invoiceNumber || line.invoiceId}</td>
                  <td className="px-5 py-3 text-right font-medium">{formatCurrency(line.amountToPay)}</td>
                  <td className="px-5 py-3 text-center">
                    <button
                      type="button"
                      onClick={() => void removeLine(line.id)}
                      className="text-xs text-red-600 hover:underline"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-5 py-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 bg-slate-50/50">
            <div className="text-sm font-semibold text-slate-700">Total: {formatCurrency(total)}</div>
            <button
              type="button"
              disabled={paying}
              onClick={() => void payViaPayu()}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              {paying ? 'Starting PayU…' : 'Pay via PayU'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
