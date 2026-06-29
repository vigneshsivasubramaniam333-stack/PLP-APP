import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { portalApi, notifyError, notifySuccess, type Invoice } from '@plp/shared';

type SubProgram = { id: string; name: string; code: string };
type EarlyPayParameter = {
  id: string;
  epDate: string;
  discountPercentage: number;
  epAmount: number;
  totalMarginAmount: number;
  consumedMarginAmount: number;
  unAllocatedAmount: number;
};
type BorrowerRow = {
  membershipId: string;
  borrowerId: string;
  borrowerName: string;
  enableEarlyPay: string;
};
type EarlyPayRequest = {
  id: string;
  invoiceId: string;
  invoiceNo: string;
  invoiceAmount: number;
  requestedAmount: number;
  cdAmount: number;
  cdPercentage: number;
  epDate: string;
  status: string;
  borrowerId: string;
};
type EarlyPayRepayment = {
  id: string;
  invoiceNo: string;
  amount: number;
  repaidAt: string;
  remarks?: string | null;
};

const TABS = ['Enable', 'Parameters', 'Requests', 'Payments', 'Closed'] as const;
type Tab = (typeof TABS)[number];

function formatInr(n: number | null | undefined) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
    n || 0,
  );
}

const cardCls = 'bg-white rounded-xl border border-slate-200 overflow-hidden';
const tableCls = 'w-full text-sm table-fixed';
const thBase = 'px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500';
const thLeft = `${thBase} text-left`;
const thRight = `${thBase} text-right`;
const thCenter = `${thBase} text-center`;
const tdBase = 'px-4 py-2.5 align-middle';
const tdRightNum = `${tdBase} text-right tabular-nums text-slate-700`;

function StatusPill({ status }: { status: string | null | undefined }) {
  const s = (status ?? '').toUpperCase();
  const cls =
    s === 'CLOSED'
      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
      : s === 'SANCTIONED_EP'
        ? 'bg-sky-50 text-sky-700 border-sky-200'
        : s === 'DISCOUNTED_EP' || s === 'REQUESTED'
          ? 'bg-amber-50 text-amber-700 border-amber-200'
          : 'bg-slate-50 text-slate-600 border-slate-200';
  return (
    <span className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${cls}`}>
      {status ?? '—'}
    </span>
  );
}

function TableEmpty({ colSpan, label }: { colSpan: number; label: string }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-4 py-10 text-center text-sm text-slate-400">
        {label}
      </td>
    </tr>
  );
}

export default function EarlyPayPage() {
  const [tab, setTab] = useState<Tab>('Enable');
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('');
  const [borrowers, setBorrowers] = useState<BorrowerRow[]>([]);
  const [parameters, setParameters] = useState<EarlyPayParameter[]>([]);
  const [requests, setRequests] = useState<EarlyPayRequest[]>([]);
  const [payments, setPayments] = useState<Invoice[]>([]);
  const [closed, setClosed] = useState<Invoice[]>([]);
  const [repayments, setRepayments] = useState<EarlyPayRepayment[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [selectedRequestIds, setSelectedRequestIds] = useState<Set<string>>(new Set());
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [paramForm, setParamForm] = useState({
    epAmount: '',
    discountPercentage: '',
    totalMarginAmount: '',
  });

  const activeSubProgram = useMemo(
    () => subPrograms.find((s) => s.id === selectedSubProgramId) ?? subPrograms[0],
    [subPrograms, selectedSubProgramId],
  );

  const loadSubPrograms = useCallback(async () => {
    try {
      const res = await portalApi.earlyPaySubPrograms();
      const list = (res.data?.data ?? []) as SubProgram[];
      setSubPrograms(list);
      if (list.length > 0 && !selectedSubProgramId) {
        setSelectedSubProgramId(list[0].id);
      }
    } catch (err) {
      notifyError(err, 'Failed to load Early Pay programs');
    } finally {
      setLoading(false);
    }
  }, [selectedSubProgramId]);

  useEffect(() => {
    void loadSubPrograms();
  }, [loadSubPrograms]);

  const subId = activeSubProgram?.id ?? '';

  const loadBorrowers = useCallback(async () => {
    if (!subId) return;
    const res = await portalApi.earlyPayBorrowers(subId);
    setBorrowers((res.data?.data ?? []) as BorrowerRow[]);
  }, [subId]);

  const loadParameters = useCallback(async () => {
    if (!subId) return;
    const res = await portalApi.earlyPayParameters(subId);
    setParameters((res.data?.data ?? []) as EarlyPayParameter[]);
  }, [subId]);

  const loadRequests = useCallback(async () => {
    const res = await portalApi.earlyPayRequests('REQUESTED', subId || undefined);
    setRequests((res.data?.data ?? []) as EarlyPayRequest[]);
    setSelectedRequestIds(new Set());
  }, [subId]);

  const loadPayments = useCallback(async () => {
    const res = await portalApi.earlyPayPayments();
    setPayments((res.data?.data ?? []) as Invoice[]);
  }, []);

  const loadClosed = useCallback(async () => {
    const [closedRes, repayRes] = await Promise.all([
      portalApi.earlyPayClosed(),
      portalApi.earlyPayRepayments(),
    ]);
    setClosed((closedRes.data?.data ?? []) as Invoice[]);
    setRepayments((repayRes.data?.data ?? []) as EarlyPayRepayment[]);
  }, []);

  useEffect(() => {
    if (!subId) return;
    if (tab === 'Enable') void loadBorrowers();
    if (tab === 'Parameters') void loadParameters();
    if (tab === 'Requests') void loadRequests();
    if (tab === 'Payments') void loadPayments();
    if (tab === 'Closed') void loadClosed();
  }, [tab, subId, loadBorrowers, loadParameters, loadRequests, loadPayments, loadClosed]);

  const toggleBorrower = async (row: BorrowerRow) => {
    const next = row.enableEarlyPay === 'YES' ? 'NO' : 'YES';
    try {
      await portalApi.earlyPayUpdateBorrower(row.membershipId, next);
      notifySuccess(next === 'YES' ? 'Early Pay enabled for borrower' : 'Early Pay disabled');
      await loadBorrowers();
    } catch (err) {
      notifyError(err, 'Update failed');
    }
  };

  const createParameter = async () => {
    if (!subId) return;
    try {
      await portalApi.earlyPayCreateParameter({
        subProgramId: subId,
        epDate: new Date().toISOString().slice(0, 10),
        epAmount: parseFloat(paramForm.epAmount),
        discountPercentage: parseFloat(paramForm.discountPercentage),
        totalMarginAmount: parseFloat(paramForm.totalMarginAmount || '0'),
      });
      notifySuccess('Early Pay parameter created');
      setParamForm({ epAmount: '', discountPercentage: '', totalMarginAmount: '' });
      await loadParameters();
    } catch (err) {
      notifyError(err, 'Create parameter failed');
    }
  };

  const approveSelected = async () => {
    if (selectedRequestIds.size === 0) return;
    try {
      await portalApi.earlyPayApproveRequests(Array.from(selectedRequestIds));
      notifySuccess('Request(s) approved');
      await loadRequests();
    } catch (err) {
      notifyError(err, 'Approve failed');
    }
  };

  const rejectSelected = async () => {
    if (selectedRequestIds.size === 0) return;
    try {
      await portalApi.earlyPayRejectRequests(Array.from(selectedRequestIds));
      notifySuccess('Request(s) rejected');
      await loadRequests();
    } catch (err) {
      notifyError(err, 'Reject failed');
    }
  };

  const uploadRepayments = async (file: File) => {
    setUploading(true);
    try {
      const res = await portalApi.earlyPayUploadRepayments(file);
      const data = res.data?.data as { processedCount?: number; remarks?: string };
      notifySuccess(data?.remarks ?? `Processed ${data?.processedCount ?? 0} row(s)`);
      await loadPayments();
      await loadClosed();
    } catch (err) {
      notifyError(err, 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const allRequestsSelected = requests.length > 0 && requests.every((r) => selectedRequestIds.has(r.id));
  const toggleAllRequests = () => {
    setSelectedRequestIds(allRequestsSelected ? new Set() : new Set(requests.map((r) => r.id)));
  };

  if (loading) {
    return <p className="text-sm text-slate-500 py-8">Loading Early Pay…</p>;
  }

  if (subPrograms.length === 0) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-5 py-6 text-sm text-amber-900">
        Early Pay is not enabled on any SBD sub-program. Enable <code>Early Pay</code> on the sub-program
        (platform admin → sub-program → Edit).
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">Early Pay</h1>
        <p className="text-sm text-slate-600 mt-1">SBD anchor-funded early settlement.</p>
      </div>

      <div className="mb-4 flex flex-wrap gap-2 items-center">
        <label className="text-xs font-medium text-slate-600">Sub-program</label>
        <select
          className="border border-slate-300 rounded-lg px-3 py-1.5 text-sm"
          value={subId}
          onChange={(e) => setSelectedSubProgramId(e.target.value)}
        >
          {subPrograms.map((sp) => (
            <option key={sp.id} value={sp.id}>
              {sp.name} ({sp.code})
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium rounded-lg border ${
              tab === t
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Enable' && (
        <div className={cardCls}>
          <table className={tableCls}>
            <colgroup>
              <col />
              <col className="w-40" />
            </colgroup>
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className={thLeft}>Borrower</th>
                <th className={thCenter}>Early Pay</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {borrowers.length === 0 ? (
                <TableEmpty colSpan={2} label="No borrowers enrolled in this sub-program." />
              ) : (
                borrowers.map((b) => (
                  <tr key={b.membershipId} className="hover:bg-slate-50/60">
                    <td className={`${tdBase} text-slate-800`}>{b.borrowerName}</td>
                    <td className={`${tdBase} text-center`}>
                      <button
                        type="button"
                        onClick={() => void toggleBorrower(b)}
                        className={`px-3 py-1 rounded-md text-xs font-semibold ${
                          b.enableEarlyPay === 'YES'
                            ? 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                        }`}
                      >
                        {b.enableEarlyPay === 'YES' ? 'Enabled' : 'Disabled'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'Parameters' && (
        <div className="space-y-6">
          <div className="bg-white rounded-xl border border-slate-200 p-5 grid gap-3 max-w-md">
            <h3 className="text-sm font-semibold text-slate-800">Create today&apos;s parameter</h3>
            <input
              className="border border-slate-300 rounded-lg px-3 py-2 text-sm"
              placeholder="Total EP amount"
              value={paramForm.epAmount}
              onChange={(e) => setParamForm((f) => ({ ...f, epAmount: e.target.value }))}
            />
            <input
              className="border border-slate-300 rounded-lg px-3 py-2 text-sm"
              placeholder="Discount %"
              value={paramForm.discountPercentage}
              onChange={(e) => setParamForm((f) => ({ ...f, discountPercentage: e.target.value }))}
            />
            <input
              className="border border-slate-300 rounded-lg px-3 py-2 text-sm"
              placeholder="Blocked margin amount"
              value={paramForm.totalMarginAmount}
              onChange={(e) => setParamForm((f) => ({ ...f, totalMarginAmount: e.target.value }))}
            />
            <button type="button" onClick={() => void createParameter()} className="bt-btn bt-btn-primary bt-btn-sm">
              Create parameter
            </button>
          </div>
          <div className={cardCls}>
            <table className={tableCls}>
              <colgroup>
                <col className="w-32" />
                <col />
                <col />
                <col />
                <col />
              </colgroup>
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className={thLeft}>Date</th>
                  <th className={thRight}>Discount %</th>
                  <th className={thRight}>EP amount</th>
                  <th className={thRight}>Unallocated</th>
                  <th className={thRight}>Blocked avail.</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {parameters.length === 0 ? (
                  <TableEmpty colSpan={5} label="No parameters configured yet." />
                ) : (
                  parameters.map((p) => (
                    <tr key={p.id} className="hover:bg-slate-50/60">
                      <td className={`${tdBase} text-slate-800`}>{p.epDate}</td>
                      <td className={tdRightNum}>{p.discountPercentage}%</td>
                      <td className={tdRightNum}>{formatInr(p.epAmount)}</td>
                      <td className={tdRightNum}>{formatInr(p.unAllocatedAmount)}</td>
                      <td className={tdRightNum}>
                        {formatInr((p.totalMarginAmount ?? 0) - (p.consumedMarginAmount ?? 0))}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'Requests' && (
        <div className="space-y-3">
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => void approveSelected()}
              disabled={selectedRequestIds.size === 0}
              className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
            >
              Approve selected
            </button>
            <button
              type="button"
              onClick={() => void rejectSelected()}
              disabled={selectedRequestIds.size === 0}
              className="bt-btn bt-btn-secondary bt-btn-sm disabled:opacity-50"
            >
              Reject selected
            </button>
          </div>
          <div className={cardCls}>
            <table className={tableCls}>
              <colgroup>
                <col className="w-10" />
                <col />
                <col />
                <col />
                <col />
              </colgroup>
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className={`${thCenter} px-3`}>
                    <input
                      type="checkbox"
                      checked={allRequestsSelected}
                      onChange={toggleAllRequests}
                      aria-label="Select all requests"
                    />
                  </th>
                  <th className={thLeft}>Invoice</th>
                  <th className={thRight}>Invoice amt</th>
                  <th className={thRight}>Discount</th>
                  <th className={thRight}>Requested (payout)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requests.length === 0 ? (
                  <TableEmpty colSpan={5} label="No pending Early Pay requests." />
                ) : (
                  requests.map((r) => (
                    <tr key={r.id} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2.5 text-center align-middle">
                        <input
                          type="checkbox"
                          checked={selectedRequestIds.has(r.id)}
                          onChange={() => {
                            setSelectedRequestIds((prev) => {
                              const next = new Set(prev);
                              if (next.has(r.id)) next.delete(r.id);
                              else next.add(r.id);
                              return next;
                            });
                          }}
                        />
                      </td>
                      <td className={`${tdBase} font-mono text-xs text-slate-800`}>{r.invoiceNo}</td>
                      <td className={tdRightNum}>{formatInr(r.invoiceAmount)}</td>
                      <td className={tdRightNum}>{formatInr(r.cdAmount)}</td>
                      <td className={`${tdRightNum} font-semibold text-slate-900`}>{formatInr(r.requestedAmount)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'Payments' && (
        <div className={cardCls}>
          <table className={tableCls}>
            <colgroup>
              <col />
              <col />
              <col />
              <col className="w-40" />
            </colgroup>
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className={thLeft}>Invoice</th>
                <th className={thRight}>Net amount</th>
                <th className={thRight}>EP payout</th>
                <th className={thCenter}>Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {payments.length === 0 ? (
                <TableEmpty colSpan={4} label="No invoices awaiting Early Pay payout." />
              ) : (
                payments.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/60">
                    <td className={`${tdBase} font-mono text-xs text-slate-800`}>{inv.invoiceNumber}</td>
                    <td className={tdRightNum}>{formatInr(inv.netAmount)}</td>
                    <td className={tdRightNum}>{formatInr(inv.earlyPayRequestedAmount)}</td>
                    <td className={`${tdBase} text-center`}>
                      <StatusPill status={inv.status} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'Closed' && (
        <div className="space-y-5">
          <div className="bg-white rounded-xl border border-slate-200 p-5">
            <h3 className="text-sm font-semibold text-slate-800">Upload repayment file</h3>
            <p className="text-xs text-slate-500 mt-1">
              CSV columns: <code>invoiceNo</code>, <code>partyCode</code> (optional), <code>amount</code>. The
              repayment is recorded against the Early Pay payout amount.
            </p>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void uploadRepayments(f);
                e.target.value = '';
              }}
            />
            <button
              type="button"
              disabled={uploading}
              onClick={() => fileInputRef.current?.click()}
              className="mt-3 inline-flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden>
                <path d="M10 3a1 1 0 01.7.29l3 3a1 1 0 11-1.4 1.42L11 5.4V13a1 1 0 11-2 0V5.4L7.7 7.71a1 1 0 01-1.4-1.42l3-3A1 1 0 0110 3z" />
                <path d="M4 14a1 1 0 011 1v1h10v-1a1 1 0 112 0v1.5A1.5 1.5 0 0115.5 18h-11A1.5 1.5 0 013 16.5V15a1 1 0 011-1z" />
              </svg>
              {uploading ? 'Uploading…' : 'Choose CSV & upload'}
            </button>
          </div>

          <div className={cardCls}>
            <div className="border-b border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Closed Early Pay invoices
            </div>
            <table className={tableCls}>
              <colgroup>
                <col />
                <col />
                <col />
                <col className="w-40" />
              </colgroup>
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className={thLeft}>Invoice</th>
                  <th className={thRight}>Net amount</th>
                  <th className={thRight}>Repaid (EP payout)</th>
                  <th className={thCenter}>Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {closed.length === 0 ? (
                  <TableEmpty colSpan={4} label="No closed Early Pay invoices yet." />
                ) : (
                  closed.map((inv) => (
                    <tr key={inv.id} className="hover:bg-slate-50/60">
                      <td className={`${tdBase} font-mono text-xs text-slate-800`}>{inv.invoiceNumber}</td>
                      <td className={tdRightNum}>{formatInr(inv.netAmount)}</td>
                      <td className={tdRightNum}>{formatInr(inv.earlyPayRequestedAmount)}</td>
                      <td className={`${tdBase} text-center`}>
                        <StatusPill status={inv.status} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className={cardCls}>
            <div className="border-b border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Repayment history
            </div>
            <table className={tableCls}>
              <colgroup>
                <col />
                <col />
                <col className="w-48" />
              </colgroup>
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className={thLeft}>Invoice</th>
                  <th className={thRight}>Repaid (EP payout)</th>
                  <th className={thLeft}>Repaid at</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {repayments.length === 0 ? (
                  <TableEmpty colSpan={3} label="No Early Pay repayments recorded yet." />
                ) : (
                  repayments.map((r) => (
                    <tr key={r.id} className="hover:bg-slate-50/60">
                      <td className={`${tdBase} font-mono text-xs text-slate-800`}>{r.invoiceNo}</td>
                      <td className={`${tdRightNum} font-semibold text-emerald-700`}>{formatInr(r.amount)}</td>
                      <td className={`${tdBase} text-xs text-slate-600`}>
                        {r.repaidAt ? new Date(r.repaidAt).toLocaleString('en-IN') : '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
