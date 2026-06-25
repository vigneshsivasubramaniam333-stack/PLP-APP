import { Link, useSearchParams } from 'react-router-dom';

export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const status = (params.get('status') ?? 'failure').toLowerCase();
  const txnId = params.get('txnId') ?? '';
  const success = status === 'success';

  return (
    <div className="max-w-lg mx-auto mt-8">
      <div
        className={`rounded-xl border p-8 text-center ${
          success ? 'bg-emerald-50 border-emerald-200' : 'bg-red-50 border-red-200'
        }`}
      >
        <h1 className={`text-xl font-bold ${success ? 'text-emerald-800' : 'text-red-800'}`}>
          {success ? 'Payment received' : 'Payment failed'}
        </h1>
        <p className="text-sm mt-3 text-slate-600">
          {success
            ? 'Your payment was recorded. Loan repayments will be applied after settlement (PRUS). You may see payment-in-progress on your invoices until then.'
            : 'The payment could not be completed. Your cart items have been restored — you can try again.'}
        </p>
        {txnId ? (
          <p className="text-xs mt-2 font-mono text-slate-500">Transaction: {txnId}</p>
        ) : null}
        <div className="mt-6 flex flex-col sm:flex-row gap-3 justify-center">
          <Link to="/invoice-discounting" className="bt-btn bt-btn-primary">
            Back to invoices
          </Link>
          {!success ? (
            <Link to="/payments/cart" className="bt-btn border border-slate-300 bg-white text-slate-700">
              View cart
            </Link>
          ) : null}
        </div>
      </div>
    </div>
  );
}
