import { useState, useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  portalApi,
  invoiceApi,
  useAuth,
  loanApi,
  paymentCartApi,
  DigitalInvoiceAttachment,
  notifyError,
  notifySuccess,
  InvoiceListToolbar,
  FLOW_PURCHASE_BILL_DISCOUNTING,
  FLOW_SALES_BILL_DISCOUNTING,
  flowTypeLabel,
  canBorrowerAcceptInvoice,
  canBorrowerRequestFinance,
  canBorrowerRequestEarlyPay,
} from '@plp/shared';
import type { Invoice, InvoiceListFilters, InvoicePageMeta, Loan, InvoiceDiscountingFlowType } from '@plp/shared';
import { InvoiceLoanRepaymentCard } from '../components/InvoiceLoanRepaymentCard';
import { InvoiceActionsMenu, type InvoiceActionItem } from '../components/InvoiceActionsMenu';
import { EarlyPayRequestModal } from '../components/EarlyPayRequestModal';

type InvoiceDiscountingPageProps = {
  flowType?: InvoiceDiscountingFlowType;
  title?: string;
  description?: string;
  createPath?: string;
  createLabel?: string;
};

/** Purchase / legacy: borrower must accept ELIGIBLE invoices before financing. */
function canShowAcceptInvoice(inv: Invoice): boolean {
  return canBorrowerAcceptInvoice(inv.status, inv.flowType);
}

/** When borrower may open the discounting request flow for this invoice. */
function canRequestDiscounting(inv: Invoice): boolean {
  return canBorrowerRequestFinance(inv.status, inv.flowType);
}

export default function InvoiceDiscountingPage({
  flowType = FLOW_PURCHASE_BILL_DISCOUNTING,
  title = 'Invoice Discounting',
  description = 'View eligible invoices and request discounting against your anchor programs.',
  createPath,
  createLabel,
}: InvoiceDiscountingPageProps = {}) {
  const { user } = useAuth();
  const borrowerId = useMemo(() => {
    if ((user?.linkedEntityType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
    return (user?.linkedEntityId ?? '').trim();
  }, [user?.linkedEntityType, user?.linkedEntityId]);

  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [pageMeta, setPageMeta] = useState<InvoicePageMeta | null>(null);
  const [listFilters, setListFilters] = useState<InvoiceListFilters>({
    search: '',
    status: '',
    lifecycle: 'active',
    page: 0,
    size: 20,
  });
  const [loansByInvoice, setLoansByInvoice] = useState<Record<string, Loan[]>>({});
  const [loading, setLoading] = useState(true);
  const [repayingLoanId, setRepayingLoanId] = useState<string | null>(null);
  const [expandedLoanInvoiceIds, setExpandedLoanInvoiceIds] = useState<Set<string>>(() => new Set());
  const [earlyPayInvoice, setEarlyPayInvoice] = useState<Invoice | null>(null);

  const toggleLoanDetails = (invoiceId: string) => {
    setExpandedLoanInvoiceIds((prev) => {
      const next = new Set(prev);
      if (next.has(invoiceId)) next.delete(invoiceId);
      else next.add(invoiceId);
      return next;
    });
  };

  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [requestedAmount, setRequestedAmount] = useState('');
  const [eligibilityResult, setEligibilityResult] = useState<Record<string, unknown> | null>(null);
  const [requestMsg, setRequestMsg] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'SMART_COLLECT' | 'PAYU_PG'>('SMART_COLLECT');
  const [selectedForCart, setSelectedForCart] = useState<Set<string>>(() => new Set());
  const [addingToCart, setAddingToCart] = useState(false);
  const [cartInvoiceIds, setCartInvoiceIds] = useState<Set<string>>(() => new Set());

  const usePayu = paymentMethod === 'PAYU_PG';

  /** Full borrower invoice list — includes FINANCING_REQUESTED etc. Avoids stale /eligible snapshots. */
  const loadInvoices = useCallback(
    async (opts?: { bustCache?: boolean }) => {
      if (!borrowerId) return;
      setLoading(true);
      try {
        await portalApi.borrowerLoans(borrowerId).catch(() => null);
        const invoiceRes = await invoiceApi.listForBorrower(borrowerId, {
          search: listFilters.search || undefined,
          status: listFilters.status || undefined,
          lifecycle: listFilters.lifecycle,
          flowType,
          page: listFilters.page,
          size: listFilters.size,
          ...(opts?.bustCache ? { _nocache: Date.now() } : {}),
        });
        const body = invoiceRes.data;
        const rows = Array.isArray(body) ? body : body?.data || [];
        setInvoices(rows);
        setPageMeta(Array.isArray(body) ? null : body?.page ?? null);

        const loanRes = await loanApi.list({ borrowerId });
        const loans = (loanRes.data?.data || []) as Loan[];
        const grouped: Record<string, Loan[]> = {};
        for (const loan of loans) {
          const invId = loan.invoiceId;
          if (!invId) continue;
          if (!grouped[invId]) grouped[invId] = [];
          grouped[invId].push(loan);
        }
        setLoansByInvoice(grouped);

        try {
          const pmRes = await paymentCartApi.paymentMethod(borrowerId);
          const pm = pmRes.data?.data?.paymentMethod;
          if (pm === 'PAYU_PG' || pm === 'SMART_COLLECT') {
            setPaymentMethod(pm);
          }
        } catch {
          setPaymentMethod('SMART_COLLECT');
        }
      } catch (err) {
        console.error(err);
        setInvoices([]);
      } finally {
        setLoading(false);
      }
    },
    [borrowerId, listFilters, flowType],
  );

  useEffect(() => {
    void loadInvoices();
  }, [loadInvoices]);

  const refreshCartInvoiceIds = useCallback(async () => {
    if (!borrowerId || paymentMethod !== 'PAYU_PG') {
      setCartInvoiceIds(new Set());
      return;
    }
    try {
      const res = await paymentCartApi.listLines(borrowerId);
      const lines = res.data?.data ?? [];
      setCartInvoiceIds(new Set(lines.map((l) => l.invoiceId)));
    } catch {
      setCartInvoiceIds(new Set());
    }
  }, [borrowerId, paymentMethod]);

  useEffect(() => {
    void refreshCartInvoiceIds();
  }, [refreshCartInvoiceIds]);

  useEffect(() => {
    const handler = () => void refreshCartInvoiceIds();
    window.addEventListener('plp-payment-cart-changed', handler);
    return () => window.removeEventListener('plp-payment-cart-changed', handler);
  }, [refreshCartInvoiceIds]);

  useEffect(() => {
    setSelectedForCart((prev) => {
      const next = new Set([...prev].filter((id) => !cartInvoiceIds.has(id)));
      return next.size === prev.size ? prev : next;
    });
  }, [cartInvoiceIds]);

  useEffect(() => {
    setSelectedInvoice((prev) => {
      if (!prev) return null;
      const fresh = invoices.find((i) => i.id === prev.id);
      return fresh ?? null;
    });
  }, [invoices]);

  const handleAcceptInvoice = async (inv: Invoice) => {
    if (!borrowerId) {
      setRequestMsg('Error: Borrower profile not linked.');
      return;
    }
    setAcceptingId(inv.id);
    setRequestMsg('');
    try {
      await invoiceApi.borrowerAccept(inv.id, borrowerId);
      setRequestMsg('Invoice accepted successfully.');
      if (selectedInvoice?.id === inv.id) {
        setSelectedInvoice(null);
        setRequestedAmount('');
        setEligibilityResult(null);
      }
      await loadInvoices();
      window.dispatchEvent(new Event('plp-borrower-loans-changed'));
    } catch (err: unknown) {
      notifyError(err, 'Failed to accept invoice');
    } finally {
      setAcceptingId(null);
    }
  };

  const checkEligibility = async () => {
    if (!selectedInvoice || !borrowerId || !selectedInvoice.programId || !requestedAmount) return;
    if (!canRequestDiscounting(selectedInvoice)) {
      setRequestMsg('Error: This invoice is not ready for discounting yet.');
      return;
    }
    setEligibilityResult(null);
    try {
      const res = await portalApi.borrowerInvoiceEligibility(
        borrowerId,
        selectedInvoice.programId,
        selectedInvoice.id,
        parseFloat(requestedAmount),
      );
      setEligibilityResult(res.data.data);
    } catch (err: unknown) {
      notifyError(err, 'Eligibility check failed');
    }
  };

  const requestDiscounting = async () => {
    if (!selectedInvoice || !borrowerId || !requestedAmount) return;
    if (!canRequestDiscounting(selectedInvoice)) {
      setRequestMsg('Error: This invoice cannot be financed in its current status.');
      return;
    }
    setRequesting(true);
    setRequestMsg('');
    const amount = parseFloat(requestedAmount);
    const cap = selectedInvoice.availableAmount ?? selectedInvoice.eligibleAmount ?? 0;
    if (amount > cap) {
      setRequestMsg(`Error: Amount cannot exceed ${cap.toFixed(2)} (available for this invoice).`);
      setRequesting(false);
      return;
    }
    try {
      await loanApi.request({
        borrowerId,
        invoiceId: selectedInvoice.id,
        productType: 'INVOICE_DISCOUNTING',
        requestedAmount: parseFloat(requestedAmount),
      });
      setRequestMsg('Discounting request submitted successfully!');
      setSelectedInvoice(null);
      setRequestedAmount('');
      setEligibilityResult(null);
      await loadInvoices({ bustCache: true });
      window.dispatchEvent(new Event('plp-borrower-loans-changed'));
    } catch (err: unknown) {
      notifyError(err, 'Request failed');
    } finally {
      setRequesting(false);
    }
  };

  const handleRepayLoan = async (loanId: string, amount: number) => {
    setRepayingLoanId(loanId);
    try {
      await loanApi.repay(loanId, amount);
      setRequestMsg('Repayment recorded successfully.');
      await loadInvoices({ bustCache: true });
      window.dispatchEvent(new Event('plp-borrower-loans-changed'));
    } catch (err: unknown) {
      notifyError(err, 'Repayment failed');
      throw err;
    } finally {
      setRepayingLoanId(null);
    }
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount || 0);

  const REPAYABLE_LOAN = new Set(['DISBURSED', 'REPAYMENT_DUE', 'OVERDUE']);

  const hasRepayableLoan = (invoiceId: string) =>
    (loansByInvoice[invoiceId] ?? []).some((l) => REPAYABLE_LOAN.has(l.status));

  const isInCart = (invoiceId: string) => cartInvoiceIds.has(invoiceId);

  const canAddToCart = (inv: Invoice) =>
    usePayu &&
    hasRepayableLoan(inv.id) &&
    !(inv.pipAmount && inv.pipAmount > 0) &&
    !isInCart(inv.id);

  const repayableAmountForInvoice = (invoiceId: string) => {
    const loan = (loansByInvoice[invoiceId] ?? []).find((l) => REPAYABLE_LOAN.has(l.status));
    if (!loan) return 0;
    return loan.outstandingAmount ?? loan.totalRepayable ?? 0;
  };

  const selectionSummary = useMemo(() => {
    let total = 0;
    for (const id of selectedForCart) {
      total += repayableAmountForInvoice(id);
    }
    return { count: selectedForCart.size, total };
  }, [selectedForCart, loansByInvoice]);

  const cartSelectableIds = useMemo(
    () => invoices.filter((inv) => canAddToCart(inv)).map((inv) => inv.id),
    [invoices, usePayu, loansByInvoice, cartInvoiceIds],
  );

  const allCartSelectableSelected =
    cartSelectableIds.length > 0 && cartSelectableIds.every((id) => selectedForCart.has(id));

  const toggleSelectAllCart = () => {
    if (allCartSelectableSelected) {
      setSelectedForCart(new Set());
      return;
    }
    setSelectedForCart(new Set(cartSelectableIds));
  };

  const buildInvoiceActions = (inv: Invoice, linkedLoans: Loan[]): InvoiceActionItem[] => {
    const items: InvoiceActionItem[] = [];
    if (linkedLoans.length > 0) {
      items.push({
        id: 'view-loan',
        label: expandedLoanInvoiceIds.has(inv.id) ? 'Hide Loan' : 'View Loan',
        onClick: () => toggleLoanDetails(inv.id),
      });
    }
    if (canAddToCart(inv)) {
      items.push({
        id: 'add-cart',
        label: 'Add to cart',
        onClick: () => void addToCart(inv.id),
        disabled: addingToCart,
      });
    }
    if (canBorrowerRequestEarlyPay(inv)) {
      items.push({
        id: 'early-pay',
        label: 'Request Early Pay',
        onClick: () => setEarlyPayInvoice(inv),
      });
    }
    return items;
  };

  const toggleCartSelect = (invoiceId: string) => {
    setSelectedForCart((prev) => {
      const next = new Set(prev);
      if (next.has(invoiceId)) next.delete(invoiceId);
      else next.add(invoiceId);
      return next;
    });
  };

  const addToCart = async (invoiceId: string) => {
    if (!borrowerId) return;
    setAddingToCart(true);
    try {
      await paymentCartApi.addLine(invoiceId, borrowerId);
      notifySuccess('Added to payment cart');
      await refreshCartInvoiceIds();
      setSelectedForCart((prev) => {
        const next = new Set(prev);
        next.delete(invoiceId);
        return next;
      });
      window.dispatchEvent(new Event('plp-payment-cart-changed'));
    } catch (err) {
      notifyError(err, 'Could not add to cart');
    } finally {
      setAddingToCart(false);
    }
  };

  const addSelectedToCart = async () => {
    if (!borrowerId || selectedForCart.size === 0) return;
    setAddingToCart(true);
    try {
      await paymentCartApi.addBulk(Array.from(selectedForCart), borrowerId);
      notifySuccess(`Added ${selectedForCart.size} invoice(s) to cart`);
      setSelectedForCart(new Set());
      await refreshCartInvoiceIds();
      window.dispatchEvent(new Event('plp-payment-cart-changed'));
    } catch (err) {
      notifyError(err, 'Bulk add failed');
    } finally {
      setAddingToCart(false);
    }
  };

  const financingRequested = selectedInvoice?.status === 'FINANCING_REQUESTED';
  const panelCanFinance =
    selectedInvoice &&
    !financingRequested &&
    canRequestDiscounting(selectedInvoice);

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">{title}</h1>
          <p className="text-sm text-slate-500 mt-1">
            {description}
            {usePayu ? ' Repayments use PayU payment gateway (add invoices to cart).' : ''}
          </p>
          {usePayu && (
            <div className="mt-3">
              <Link to="/payments/cart" className="text-sm font-semibold text-sky-700 hover:underline">
                View payment cart →
              </Link>
            </div>
          )}
        </div>
        {createPath ? (
          <Link
            to={createPath}
            className="bt-btn bt-btn-primary shrink-0"
          >
            {createLabel ?? 'Create'}
          </Link>
        ) : null}
      </div>

      {loading && invoices.length === 0 && (
        <div className="mb-6 py-12 text-center text-slate-400 text-sm">Loading invoices…</div>
      )}

      {!borrowerId && (
        <p className="mb-6 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to a borrower record.
        </p>
      )}

      {requestMsg && (
        <div
          className={`mb-4 p-4 rounded-lg text-sm flex items-center gap-2 ${
            requestMsg.startsWith('Error')
              ? 'bg-red-50 text-red-700 border border-red-200'
              : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
          }`}
        >
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            {requestMsg.startsWith('Error') ? (
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            ) : (
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            )}
          </svg>
          {requestMsg}
        </div>
      )}

      <InvoiceListToolbar
        filters={listFilters}
        pageMeta={pageMeta}
        onChange={(next) => setListFilters((f) => ({ ...f, ...next }))}
      />

      {usePayu && selectionSummary.count > 0 && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-sky-200 bg-sky-50/80 px-4 py-3">
          <p className="text-sm text-slate-700">
            <span className="font-semibold text-sky-900">{selectionSummary.count}</span> invoice
            {selectionSummary.count === 1 ? '' : 's'} selected
            <span className="mx-2 text-slate-300" aria-hidden>
              |
            </span>
            Total repayment:{' '}
            <span className="font-semibold tabular-nums text-slate-900">
              {formatCurrency(selectionSummary.total)}
            </span>
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setSelectedForCart(new Set())}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-800"
            >
              Clear selection
            </button>
            <button
              type="button"
              disabled={addingToCart}
              onClick={() => void addSelectedToCart()}
              className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
            >
              Add selected to cart
            </button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm mb-6">
        <div className="px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-700">Your invoices</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {usePayu && (
                  <th className="px-3 py-3 w-10 text-center">
                    {cartSelectableIds.length > 0 ? (
                      <input
                        type="checkbox"
                        checked={allCartSelectableSelected}
                        onChange={toggleSelectAllCart}
                        aria-label="Select all repayable invoices"
                      />
                    ) : null}
                  </th>
                )}
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Invoice #</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Flow</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Due Date</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Net Amount</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Eligible</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Available</th>
                {usePayu && (
                  <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">PRUS</th>
                )}
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Copy</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="px-3 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider w-36">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {invoices.length === 0 ? (
                <tr>
                    <td colSpan={usePayu ? 10 : 9} className="px-5 py-12 text-center text-slate-400 text-sm">
                    No invoices yet.
                  </td>
                </tr>
              ) : (
                invoices.flatMap((inv) => {
                  const linkedLoans = loansByInvoice[inv.id] ?? [];
                  const rows = [
                    <tr key={inv.id} className={`hover:bg-slate-50/80 ${selectedInvoice?.id === inv.id ? 'bg-sky-50/50' : ''}`}>
                    {usePayu && (
                      <td className="px-3 py-3 text-center align-middle">
                        {isInCart(inv.id) ? (
                          <span
                            className="inline-block rounded bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-800"
                            title="Already in payment cart"
                          >
                            In cart
                          </span>
                        ) : canAddToCart(inv) ? (
                          <input
                            type="checkbox"
                            checked={selectedForCart.has(inv.id)}
                            onChange={() => toggleCartSelect(inv.id)}
                            aria-label={`Select invoice ${inv.invoiceNumber}`}
                          />
                        ) : null}
                      </td>
                    )}
                    <td className="px-5 py-3 font-mono text-xs font-medium text-slate-700">{inv.invoiceNumber}</td>
                    <td className="px-5 py-3 text-xs text-slate-600">
                      {flowTypeLabel(inv.flowType)}
                    </td>
                    <td className="px-5 py-3 text-xs text-slate-600">{inv.dueDate}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(inv.netAmount)}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(inv.eligibleAmount)}</td>
                    <td className="px-5 py-3 text-right font-medium text-slate-800">{formatCurrency(inv.availableAmount)}</td>
                    {usePayu && (
                      <td className="px-5 py-3 text-right text-amber-700 text-xs font-medium tabular-nums align-middle">
                        {inv.pipAmount && inv.pipAmount > 0 ? formatCurrency(inv.pipAmount) : '—'}
                      </td>
                    )}
                    <td className="px-5 py-3 text-center align-middle">
                      <DigitalInvoiceAttachment invoiceId={inv.id} fileName={inv.digitalInvoiceFileName} />
                    </td>
                    <td className="px-5 py-3 text-center align-middle">
                      <InvoiceBadge status={inv.status} />
                    </td>
                    <td className="px-3 py-3 align-top">
                      <div className="flex w-36 flex-col items-stretch gap-1.5">
                        <div className="flex justify-end">
                          <InvoiceActionsMenu
                            items={buildInvoiceActions(inv, linkedLoans)}
                            busy={addingToCart || acceptingId === inv.id}
                          />
                        </div>
                        {canShowAcceptInvoice(inv) && (
                          <button
                            type="button"
                            disabled={!borrowerId || acceptingId === inv.id}
                            onClick={() => void handleAcceptInvoice(inv)}
                            className="bt-btn bt-btn-primary bt-btn-sm w-full disabled:opacity-50"
                          >
                            {acceptingId === inv.id ? 'Accepting...' : 'Accept Invoice'}
                          </button>
                        )}
                        {canRequestDiscounting(inv) && (
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedInvoice(inv);
                              setRequestedAmount(inv.availableAmount?.toString() || '');
                              setEligibilityResult(null);
                            }}
                            className="bt-btn bt-btn-primary bt-btn-sm w-full"
                          >
                            Select
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>,
                  ];
                  if (linkedLoans.length > 0 && expandedLoanInvoiceIds.has(inv.id)) {
                    rows.push(
                      <tr key={`${inv.id}-loans`}>
                        <td colSpan={usePayu ? 10 : 9} className="px-5 pb-4 bg-slate-50/40">
                          {linkedLoans.map((loan) =>
                            usePayu ? (
                              <div key={loan.id} className="text-xs text-slate-600 py-2 border-b border-slate-100 last:border-0">
                                Loan {loan.loanNumber || loan.id} — {loan.status}
                                {inv.pipAmount && inv.pipAmount > 0 ? (
                                  <span className="ml-2 text-amber-700 font-medium">
                                    PRUS: {formatCurrency(inv.pipAmount)} (pending settlement)
                                  </span>
                                ) : null}
                              </div>
                            ) : (
                              <InvoiceLoanRepaymentCard
                                key={loan.id}
                                invoice={inv}
                                loan={loan}
                                repaying={repayingLoanId === loan.id}
                                onRepay={handleRepayLoan}
                              />
                            ),
                          )}
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
      </div>

      {selectedInvoice && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-slate-700 mb-4">
            Request Discounting — Invoice #{selectedInvoice.invoiceNumber}
          </h3>

          {financingRequested && (
            <div className="mb-4 p-3 rounded-lg border border-violet-200 bg-violet-50 text-violet-900 text-sm">
              Financing has already been requested for this invoice.
            </div>
          )}
          {!panelCanFinance && !financingRequested && (
            <div className="mb-4 p-3 rounded-lg border border-amber-200 bg-amber-50 text-amber-900 text-sm">
              This invoice cannot be financed yet. Purchase-flow invoices must be accepted first when status is ELIGIBLE.
            </div>
          )}

          {selectedInvoice.digitalInvoiceFileName && (
            <div className="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <span className="text-slate-600">
                Invoice copy:{' '}
                <span className="font-mono text-xs text-slate-800">{selectedInvoice.digitalInvoiceFileName}</span>
              </span>
              <DigitalInvoiceAttachment
                invoiceId={selectedInvoice.id}
                fileName={selectedInvoice.digitalInvoiceFileName}
              />
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-5">
            <div className="bg-slate-50 rounded-lg p-3">
              <div className="text-xs text-slate-500">Net Amount</div>
              <div className="text-lg font-bold text-slate-800 mt-0.5">{formatCurrency(selectedInvoice.netAmount)}</div>
            </div>
            <div className="bg-emerald-50 rounded-lg p-3">
              <div className="text-xs text-emerald-600">Eligible Amount</div>
              <div className="text-lg font-bold text-emerald-700 mt-0.5">{formatCurrency(selectedInvoice.eligibleAmount)}</div>
            </div>
            <div className="bg-sky-50 rounded-lg p-3">
              <div className="text-xs text-sky-600">Available Amount</div>
              <div className="text-lg font-bold text-sky-700 mt-0.5">{formatCurrency(selectedInvoice.availableAmount)}</div>
            </div>
          </div>

          <div className="flex flex-wrap items-end gap-3 mb-4">
            <div className="flex-1 min-w-[160px]">
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Requested Amount</label>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-sm">&#8377;</span>
                <input
                  type="number"
                  step="0.01"
                  value={requestedAmount}
                  onChange={(e) => setRequestedAmount(e.target.value)}
                  max={selectedInvoice.availableAmount}
                  disabled={financingRequested || !panelCanFinance}
                  className="w-full pl-8 pr-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-sky-500 focus:bg-white outline-none disabled:opacity-50"
                />
              </div>
            </div>
            <button
              type="button"
              onClick={() => void checkEligibility()}
              disabled={
                financingRequested || !panelCanFinance || !requestedAmount || !selectedInvoice?.programId
              }
              className="px-4 py-2.5 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50"
            >
              Check Eligibility
            </button>
            <button
              type="button"
              onClick={() => void requestDiscounting()}
              disabled={
                requesting ||
                financingRequested ||
                !panelCanFinance ||
                !requestedAmount ||
                (eligibilityResult !== null && !eligibilityResult.eligible)
              }
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              {requesting ? 'Submitting...' : 'Request Discounting'}
            </button>
          </div>

          {eligibilityResult && (
            <div
              className={`p-4 rounded-lg text-sm border ${
                eligibilityResult.eligible ? 'bg-emerald-50 border-emerald-200' : 'bg-red-50 border-red-200'
              }`}
            >
              <p className="font-semibold mb-1">
                {eligibilityResult.eligible ? 'Eligible for discounting' : 'Not eligible'}
              </p>
              {typeof eligibilityResult.eligibleAmount === 'number' ? (
                <p className="text-xs">Max eligible: {formatCurrency(eligibilityResult.eligibleAmount)}</p>
              ) : null}
              {(eligibilityResult.reasons as string[] | undefined)?.map((r: string, i: number) => (
                <p key={i} className="text-xs text-red-600 mt-1">
                  {r}
                </p>
              ))}
            </div>
          )}
        </div>
      )}
      {earlyPayInvoice && (
        <EarlyPayRequestModal
          invoice={earlyPayInvoice}
          onClose={() => setEarlyPayInvoice(null)}
          onSuccess={() => void loadInvoices({ bustCache: true })}
        />
      )}
    </div>
  );
}

function InvoiceBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    VERIFIED: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
    ELIGIBLE: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    BORROWER_ACCEPTED: 'bg-violet-50 text-violet-700 ring-1 ring-violet-600/20',
    FINANCING_REQUESTED: 'bg-indigo-50 text-indigo-800 ring-1 ring-indigo-600/25',
    PARTIALLY_DISCOUNTED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    FULLY_DISCOUNTED: 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/20',
    DISCOUNTED_EP: 'bg-rose-50 text-rose-800 ring-1 ring-rose-600/20',
    SANCTIONED_EP: 'bg-fuchsia-50 text-fuchsia-800 ring-1 ring-fuchsia-600/20',
    CLOSED: 'bg-slate-100 text-slate-600 ring-1 ring-slate-400/20',
  };
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${styles[status] || 'bg-slate-50 text-slate-600'}`}
    >
      {status}
    </span>
  );
}
