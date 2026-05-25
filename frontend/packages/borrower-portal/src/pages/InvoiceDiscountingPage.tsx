import { useState, useEffect, useCallback, useMemo } from 'react';
import { portalApi, invoiceApi, extractApiErrorMessage, useAuth, apiClient, loanApi, openDigitalInvoiceDownload } from '@plp/shared';
import type { Invoice } from '@plp/shared';

const FLOW_PURCHASE = 'PURCHASE_BILL_DISCOUNTING';
const FLOW_SALES = 'SALES_BILL_DISCOUNTING';

function isPurchaseFlow(inv: Invoice): boolean {
  const ft = inv.flowType?.trim();
  return !ft || ft === FLOW_PURCHASE;
}

function isSalesFlow(inv: Invoice): boolean {
  return inv.flowType?.trim() === FLOW_SALES;
}

/** Purchase / legacy: borrower must accept ELIGIBLE invoices before financing. */
function canShowAcceptInvoice(inv: Invoice): boolean {
  return isPurchaseFlow(inv) && inv.status === 'ELIGIBLE';
}

/** When borrower may open the discounting request flow for this invoice. */
function canRequestDiscounting(inv: Invoice): boolean {
  if (inv.status === 'FINANCING_REQUESTED') return false;
  if (isPurchaseFlow(inv)) {
    return inv.status === 'BORROWER_ACCEPTED' || inv.status === 'PARTIALLY_DISCOUNTED';
  }
  if (isSalesFlow(inv)) {
    return inv.status === 'ELIGIBLE' || inv.status === 'PARTIALLY_DISCOUNTED';
  }
  return inv.status === 'BORROWER_ACCEPTED' || inv.status === 'PARTIALLY_DISCOUNTED';
}

export default function InvoiceDiscountingPage() {
  const { user } = useAuth();
  const borrowerId = useMemo(() => {
    if ((user?.linkedEntityType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
    return (user?.linkedEntityId ?? '').trim();
  }, [user?.linkedEntityType, user?.linkedEntityId]);

  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);

  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [requestedAmount, setRequestedAmount] = useState('');
  const [eligibilityResult, setEligibilityResult] = useState<Record<string, unknown> | null>(null);
  const [requestMsg, setRequestMsg] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);

  /** Full borrower invoice list — includes FINANCING_REQUESTED etc. Avoids stale /eligible snapshots. */
  const loadInvoices = useCallback(
    async (opts?: { bustCache?: boolean }) => {
      if (!borrowerId) return;
      setLoading(true);
      try {
        await portalApi.borrowerLoans(borrowerId).catch(() => null);
        const invoiceRes = await apiClient.get<Invoice[]>(`/api/v1/invoices/borrower/${borrowerId}`, {
          params: opts?.bustCache ? { _nocache: Date.now() } : undefined,
        });
        const rows = invoiceRes.data || [];
        setInvoices(rows);
      } catch (err) {
        console.error(err);
        setInvoices([]);
      } finally {
        setLoading(false);
      }
    },
    [borrowerId],
  );

  useEffect(() => {
    void loadInvoices();
  }, [loadInvoices]);

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
      setRequestMsg('Error: ' + extractApiErrorMessage(err, 'Failed to accept invoice'));
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
      setRequestMsg('Error: ' + extractApiErrorMessage(err, 'Eligibility check failed'));
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
      setRequestMsg('Error: ' + extractApiErrorMessage(err, 'Request failed'));
    } finally {
      setRequesting(false);
    }
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount || 0);

  const financingRequested = selectedInvoice?.status === 'FINANCING_REQUESTED';
  const panelCanFinance =
    selectedInvoice &&
    !financingRequested &&
    canRequestDiscounting(selectedInvoice);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Invoice Discounting</h1>
        <p className="text-sm text-slate-500 mt-1">
          Purchase-flow invoices must be accepted before requesting financing. Sales-flow invoices can proceed when eligible.
          Invoices load automatically from your borrower profile.
        </p>
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

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden mb-6">
        <div className="px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-700">Your invoices</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Invoice #</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Flow</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Due Date</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Net Amount</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Eligible</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Available</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Digital invoice
                </th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {invoices.length === 0 ? (
                <tr>
                  <td colSpan={9} className="px-5 py-12 text-center text-slate-400 text-sm">
                    No invoices yet.
                  </td>
                </tr>
              ) : (
                invoices.map((inv) => (
                  <tr key={inv.id} className={`hover:bg-slate-50/80 ${selectedInvoice?.id === inv.id ? 'bg-sky-50/50' : ''}`}>
                    <td className="px-5 py-3 font-mono text-xs font-medium text-slate-700">{inv.invoiceNumber}</td>
                    <td className="px-5 py-3 text-xs text-slate-600">
                      {isSalesFlow(inv) ? 'Sales' : 'Purchase'}
                    </td>
                    <td className="px-5 py-3 text-xs text-slate-600">{inv.dueDate}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(inv.netAmount)}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{formatCurrency(inv.eligibleAmount)}</td>
                    <td className="px-5 py-3 text-right font-medium text-slate-800">{formatCurrency(inv.availableAmount)}</td>
                    <td className="px-5 py-3 text-xs text-slate-600 max-w-[160px]">
                      {inv.digitalInvoiceFileName ? (
                        <div className="flex flex-col gap-1 items-start">
                          <span className="font-mono text-[11px] text-slate-700 break-all">{inv.digitalInvoiceFileName}</span>
                          <div className="flex flex-wrap gap-x-2 gap-y-0.5">
                            <button
                              type="button"
                              onClick={() => {
                                void openDigitalInvoiceDownload(inv.id).catch((e: unknown) => {
                                  window.alert(extractApiErrorMessage(e, 'Could not open digital invoice'));
                                });
                              }}
                              className="text-xs font-semibold text-sky-700 hover:text-sky-900 underline"
                            >
                              View Invoice
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                void openDigitalInvoiceDownload(inv.id).catch((e: unknown) => {
                                  window.alert(extractApiErrorMessage(e, 'Could not download digital invoice'));
                                });
                              }}
                              className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline"
                            >
                              Download Invoice
                            </button>
                          </div>
                        </div>
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-center">
                      <InvoiceBadge status={inv.status} />
                    </td>
                    <td className="px-5 py-3 text-center">
                      <div className="flex flex-col items-center gap-1.5">
                        {canShowAcceptInvoice(inv) && (
                          <button
                            type="button"
                            disabled={!borrowerId || acceptingId === inv.id}
                            onClick={() => void handleAcceptInvoice(inv)}
                            className="px-3 py-1.5 text-xs font-semibold bg-emerald-600 text-white rounded-md hover:bg-emerald-700 disabled:opacity-50"
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
                            className="px-3 py-1.5 text-xs font-semibold bg-sky-600 text-white rounded-md hover:bg-sky-700"
                          >
                            Select
                          </button>
                        )}
                        {!canShowAcceptInvoice(inv) && !canRequestDiscounting(inv) && (
                          <span className="text-[11px] text-slate-400">—</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
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
                Digital invoice:{' '}
                <span className="font-mono text-xs text-slate-800">{selectedInvoice.digitalInvoiceFileName}</span>
              </span>
              <button
                type="button"
                onClick={() => {
                  void openDigitalInvoiceDownload(selectedInvoice.id).catch((e: unknown) => {
                    window.alert(extractApiErrorMessage(e, 'Could not open digital invoice'));
                  });
                }}
                className="text-xs font-semibold text-sky-700 hover:text-sky-900 underline"
              >
                View Invoice
              </button>
              <button
                type="button"
                onClick={() => {
                  void openDigitalInvoiceDownload(selectedInvoice.id).catch((e: unknown) => {
                    window.alert(extractApiErrorMessage(e, 'Could not download digital invoice'));
                  });
                }}
                className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline"
              >
                Download Invoice
              </button>
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
              className="px-4 py-2.5 bg-sky-600 text-white rounded-lg text-sm font-semibold hover:bg-sky-700 disabled:opacity-50"
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
  };
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${styles[status] || 'bg-slate-50 text-slate-600'}`}
    >
      {status}
    </span>
  );
}
