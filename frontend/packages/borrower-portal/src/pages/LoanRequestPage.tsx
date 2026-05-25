import { useCallback, useEffect, useState } from 'react';
import {
  apiClient,
  borrowerApi,
  extractApiErrorMessage,
  loanApi,
  salaryApi,
  useAuth,
} from '@plp/shared';
import type { EligibilityResult, EmployeeSalaryData } from '@plp/shared';

function slipSelectable(slip: EmployeeSalaryData): boolean {
  const st = slip.slipStatus;
  if (!st) return true;
  return st === 'AVAILABLE_FOR_LOAN' || st === 'REJECTED_AVAILABLE_AGAIN';
}

function slipBlockedReason(slip: EmployeeSalaryData): string {
  if (slipSelectable(slip)) return '';
  switch (slip.slipStatus) {
    case 'LOAN_REQUESTED':
      return 'A loan is already in progress for this slip.';
    case 'DISBURSED_USED':
      return 'This slip was already used for a disbursed loan.';
    case 'CLOSED_USED':
      return 'This slip is permanently closed after repayment.';
    default:
      return 'This slip cannot be used for a new loan.';
  }
}

/** Normalize GET /salary list: body may be `[...]`, `{ data: [...] }`, or axios already on `.data`. */
function salarySlipsFromListResponse(res: { data?: unknown }): EmployeeSalaryData[] {
  const payload = res.data ?? res;
  const slips = Array.isArray(payload) ? payload : (payload as { data?: unknown })?.data ?? [];
  return Array.isArray(slips) ? (slips as EmployeeSalaryData[]) : [];
}

function formatInr(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(amount || 0);
}

const SUCCESS_LOCK_MESSAGE =
  'Pay loan request submitted successfully. This salary slip is now locked for this request.';

export default function LoanRequestPage() {
  const { user } = useAuth();
  const borrowerId =
    (user?.linkedEntityType ?? '').trim().toUpperCase() === 'BORROWER' ? (user?.linkedEntityId?.trim() ?? '') : '';

  const [loading, setLoading] = useState(true);
  const [initError, setInitError] = useState('');
  const [noSalary, setNoSalary] = useState(false);
  /** Program id resolved from borrower profile — used for eligibility reloads */
  const [borrowerProgramId, setBorrowerProgramId] = useState('');
  /** Salary-derived probe passed as requestedAmount to eligibility API */
  const [eligibleProbeAmount, setEligibleProbeAmount] = useState<number | null>(null);
  const [eligibility, setEligibility] = useState<EligibilityResult | null>(null);
  const [loanAmount, setLoanAmount] = useState('');
  const [requesting, setRequesting] = useState(false);
  /** Brief spinner after submit while eligibility is re-fetched (cache-busted). */
  const [refreshingEligibilityAfterSubmit, setRefreshingEligibilityAfterSubmit] = useState(false);
  /** True only when eligibility reload failed after a successful POST (borrower sees reload hint). */
  const [postSubmitEligibilityRefreshFailed, setPostSubmitEligibilityRefreshFailed] = useState(false);
  const [banner, setBanner] = useState<{ ok: boolean; text: string } | null>(null);
  /** After a successful POST: hide form until eligibility says eligible again (e.g. new salary period). */
  const [payLoanSubmitSuccessLocked, setPayLoanSubmitSuccessLocked] = useState(false);
  const [salarySlips, setSalarySlips] = useState<EmployeeSalaryData[]>([]);
  const [selectedSalaryDataId, setSelectedSalaryDataId] = useState<string>('');

  const fetchEligibility = useCallback(
    async (
      programIdForEligibility: string,
      probe: number,
      options?: { bustCache?: boolean; salaryDataId?: string },
    ) => {
      const params: Record<string, string | number> = {
        borrowerId,
        programId: programIdForEligibility,
        requestedAmount: probe,
      };
      if (options?.salaryDataId) {
        params.salaryDataId = options.salaryDataId;
      }
      if (options?.bustCache) {
        params._nocache = Date.now();
      }
      const elRes = await apiClient.get('/api/v1/portal/borrower/eligibility', { params });
      const data = elRes.data?.data as EligibilityResult | undefined;
      if (!data) {
        throw new Error('NO_ELIGIBILITY_DATA');
      }
      return data;
    },
    [borrowerId],
  );

  /** After reload: if borrower is eligible again (new salary window), unlock the success-only UI. */
  const applyEligibilityFromServer = useCallback((data: EligibilityResult, probeFloor: number) => {
    setEligibility(data);
    if (data.eligible) {
      setPayLoanSubmitSuccessLocked(false);
    }
    const maxAmt = Number(data.eligibleAmount);
    const nextLoanAmount =
      Number.isFinite(maxAmt) && maxAmt > 0 ? String(Math.floor(maxAmt)) : String(Math.floor(probeFloor));
    setLoanAmount(nextLoanAmount);
  }, []);

  const loadEligibilityAfterInit = useCallback(
    async (programIdForEligibility: string, probe: number, bustCache?: boolean, salaryDataId?: string) => {
      const data = await fetchEligibility(programIdForEligibility, probe, { bustCache, salaryDataId });
      applyEligibilityFromServer(data, probe);
      return data;
    },
    [fetchEligibility, applyEligibilityFromServer],
  );

  const onSelectSalarySlip = useCallback(
    async (slipId: string) => {
      setSelectedSalaryDataId(slipId);
      if (!borrowerProgramId) return;
      const slip = salarySlips.find((s) => s.id === slipId);
      const probe =
        slip != null && Number.isFinite(Number(slip.eligibleAmount))
          ? Number(slip.eligibleAmount)
          : eligibleProbeAmount ?? 10_000;
      try {
        const data = await fetchEligibility(borrowerProgramId, probe, { salaryDataId: slipId });
        applyEligibilityFromServer(data, probe);
      } catch {
        /* ignore */
      }
    },
    [borrowerProgramId, salarySlips, eligibleProbeAmount, fetchEligibility, applyEligibilityFromServer],
  );

  useEffect(() => {
    setBanner(null);
    setInitError('');
    setNoSalary(false);
    setEligibility(null);
    setLoanAmount('');
    setBorrowerProgramId('');
    setEligibleProbeAmount(null);
    setPayLoanSubmitSuccessLocked(false);
    setRefreshingEligibilityAfterSubmit(false);
    setPostSubmitEligibilityRefreshFailed(false);
    setSalarySlips([]);
    setSelectedSalaryDataId('');

    if (!borrowerId) {
      setLoading(false);
      setInitError('Your account is not linked to a borrower profile. Contact support.');
      return;
    }

    let cancelled = false;

    (async () => {
      setLoading(true);
      try {
        const [borrowerRes, salaryListRes] = await Promise.all([
          borrowerApi.get(borrowerId),
          salaryApi.list({ borrowerId }),
        ]);

        if (cancelled) return;

        const borrower = borrowerRes.data?.data as { programId?: string } | undefined;
        const resolvedProgramId = borrower?.programId;
        if (!resolvedProgramId) {
          setInitError('Borrower profile is missing program information. Contact support.');
          return;
        }

        const rows = salarySlipsFromListResponse(salaryListRes);
        const forProgram = rows.filter((r) => r.programId === resolvedProgramId);

        if (forProgram.length === 0) {
          setNoSalary(true);
          return;
        }

        setSalarySlips(forProgram);

        const preferred =
  	  forProgram.find((s) => slipSelectable(s) && Number(s.eligibleAmount) > 0) ?? forProgram[0];
        setSelectedSalaryDataId(preferred.id);

        const rawEligible = preferred.eligibleAmount;
        const probeAmount =
          typeof rawEligible === 'number' ? rawEligible : Number.parseFloat(String(rawEligible));
        

        setBorrowerProgramId(resolvedProgramId);
        setEligibleProbeAmount(probeAmount);

        const data = await fetchEligibility(resolvedProgramId, probeAmount, { salaryDataId: preferred.id });
        if (cancelled) return;

        applyEligibilityFromServer(data, probeAmount);
      } catch (err: unknown) {
        if (!cancelled) {
          setInitError(extractApiErrorMessage(err, 'Could not load pay loan details'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [borrowerId, fetchEligibility, applyEligibilityFromServer]);

  const handleRequestPayLoan = async () => {
    if (!borrowerId || !borrowerProgramId || !selectedSalaryDataId || !eligibility?.eligible || payLoanSubmitSuccessLocked || requesting)
      return;
    const requestedAmount = Number.parseFloat(loanAmount.replace(/,/g, ''));
    if (!Number.isFinite(requestedAmount) || requestedAmount <= 0) {
      setBanner({ ok: false, text: 'Enter a valid loan amount.' });
      return;
    }

    setBanner(null);
    setPostSubmitEligibilityRefreshFailed(false);
    setRequesting(true);
    try {
      await loanApi.request({
        borrowerId,
        programId: borrowerProgramId,
        productType: 'PAY_DAY_LOAN',
        requestedAmount,
        salaryDataId: selectedSalaryDataId,
      });
      setPayLoanSubmitSuccessLocked(true);
      setRefreshingEligibilityAfterSubmit(true);
      try {
        await loadEligibilityAfterInit(
          borrowerProgramId,
          eligibleProbeAmount ?? requestedAmount,
          true,
          selectedSalaryDataId,
        );
        setBanner(null);
        setPostSubmitEligibilityRefreshFailed(false);
      } catch {
        setPostSubmitEligibilityRefreshFailed(true);
      } finally {
        setRefreshingEligibilityAfterSubmit(false);
      }
    } catch (err: unknown) {
      setPayLoanSubmitSuccessLocked(false);
      setBanner({ ok: false, text: extractApiErrorMessage(err, 'Loan request failed') });
    } finally {
      setRequesting(false);
    }
  };

  /** After submit: eligible form hidden until eligibility is eligible again (new salary window). */
  const showPayLoanForm = Boolean(eligibility?.eligible && !payLoanSubmitSuccessLocked);

  /** Salary-slip reuse / concurrent wording from backend reasons */
  const showSalarySlipContext =
    eligibility?.reasons?.some((r) => {
      if (typeof r !== 'string') return false;
      const t = r.toLowerCase();
      return (
        t.includes('salary slip') ||
        t.includes('salary period') ||
        t.includes('already exists') ||
        t.includes('already been used')
      );
    }) ?? false;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Request Pay Loan</h1>
        <p className="text-sm text-slate-500 mt-1">
          We check your salary and eligibility automatically—no program selection needed.
        </p>
      </div>

      <div className="max-w-2xl">
        {banner && (
          <div
            className={`mb-4 p-3 rounded-lg text-sm border ${
              banner.ok
                ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                : 'bg-red-50 text-red-700 border-red-200'
            }`}
          >
            {banner.text}
          </div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          {loading && (
            <div className="text-sm text-slate-500 py-8 text-center">Checking your eligibility…</div>
          )}

          {!loading && initError && (
            <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-3">
              {initError}
            </div>
          )}

          {!loading && !initError && noSalary && (
            <p className="text-sm text-slate-700 leading-relaxed">
              No salary data available. Contact your employer.
            </p>
          )}

          {!loading && !initError && !noSalary && eligibility && (
            <div className="space-y-5">
              {salarySlips.length > 0 && (
                <div className="rounded-lg border border-slate-200 bg-slate-50/90 p-4">
                  <p className="text-sm font-medium text-slate-800 mb-3">Select salary slip</p>
                  <ul className="space-y-2">
                    {salarySlips.map((slip) => {
                      const blocked = !slipSelectable(slip);
                      const active = selectedSalaryDataId === slip.id;
                      return (
                        <li key={slip.id}>
                          <label
                            className={`flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 rounded-lg border px-3 py-2.5 cursor-pointer text-sm ${
                              active ? 'border-sky-500 bg-sky-50' : 'border-slate-200 bg-white'
                            } ${blocked ? 'opacity-60 cursor-not-allowed' : ''}`}
                          >
                            <span className="flex items-center gap-2">
                              <input
                                type="radio"
                                name="salarySlip"
                                className="text-sky-600"
                                checked={active}
                                disabled={
                                  blocked || requesting || refreshingEligibilityAfterSubmit || payLoanSubmitSuccessLocked
                                }
                                onChange={() => void onSelectSalarySlip(slip.id)}
                              />
                              <span className="font-medium text-slate-800">
                                {slip.salarySlipNumber ?? slip.id.slice(0, 8) + '…'}
                              </span>
                              {slip.externalReferenceNumber ? (
                                <span className="text-slate-500">Ref: {slip.externalReferenceNumber}</span>
                              ) : null}
                            </span>
                            <span className="text-slate-600 pl-6 sm:pl-0">
                              {slip.payPeriod}
                              {slip.slipStatus ? (
                                <span className="ml-2 text-xs font-medium text-slate-500">· {slip.slipStatus}</span>
                              ) : null}
                            </span>
                          </label>
                          {blocked ? (
                            <p className="text-xs text-amber-800 mt-1 pl-7">{slipBlockedReason(slip)}</p>
                          ) : null}
                        </li>
                      );
                    })}
                  </ul>
                </div>
              )}
              {payLoanSubmitSuccessLocked && (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4">
                  <p className="text-sm font-semibold text-emerald-900 mb-1">Request submitted</p>
                  <p className="text-sm text-emerald-900 leading-relaxed">{SUCCESS_LOCK_MESSAGE}</p>
                </div>
              )}

              {refreshingEligibilityAfterSubmit && (
                <p className="text-sm text-slate-600 text-center py-2">Updating your eligibility…</p>
              )}

              {!eligibility.eligible && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4">
                  <p className="text-sm font-semibold text-red-800 mb-2">Not eligible for a pay loan right now</p>
                  {showSalarySlipContext ? (
                    <p className="text-sm text-red-800 font-medium mb-2">
                      Your current salary slip cannot be used for another Pay Loan. Details below:
                    </p>
                  ) : null}
                  {eligibility.reasons?.length ? (
                    <ul className="text-sm text-red-800 space-y-1.5 list-disc pl-4">
                      {eligibility.reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-red-800">Please try again later or contact support.</p>
                  )}
                </div>
              )}

              {payLoanSubmitSuccessLocked && postSubmitEligibilityRefreshFailed && eligibility.eligible && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
                  Your request was saved; we couldn&apos;t confirm updated eligibility yet. Reload the page to see the
                  latest status.
                </div>
              )}

              {showPayLoanForm ? (
                <>
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-1">
                      Eligible amount
                    </p>
                    <p className="text-2xl font-bold text-slate-900">{formatInr(eligibility.eligibleAmount)}</p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1.5">Enter loan amount</label>
                    <div className="relative">
                      <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-sm">&#8377;</span>
                      <input
                        type="number"
                        min={1}
                        step={100}
                        value={loanAmount}
                        onChange={(e) => setLoanAmount(e.target.value)}
                        disabled={requesting || refreshingEligibilityAfterSubmit}
                        className="w-full pl-8 pr-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-sky-500 focus:bg-white outline-none disabled:opacity-60"
                      />
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1">Defaults to your maximum eligible amount; you can enter less.</p>
                  </div>

                  <button
                    type="button"
                    onClick={() => void handleRequestPayLoan()}
                    disabled={
                      requesting ||
                      refreshingEligibilityAfterSubmit ||
                      payLoanSubmitSuccessLocked ||
                      !eligibility.eligible
                    }
                    className="w-full bg-sky-600 text-white py-2.5 px-4 rounded-lg text-sm font-semibold hover:bg-sky-700 disabled:opacity-50"
                  >
                    {requesting || refreshingEligibilityAfterSubmit ? 'Submitting…' : 'Request Pay Loan'}
                  </button>
                </>
              ) : null}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
