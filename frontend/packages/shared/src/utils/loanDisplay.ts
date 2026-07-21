import type { Loan } from '../types';

function n(v: unknown): number {
  const x = Number(v);
  return Number.isFinite(x) ? x : 0;
}

/** Principal shown in loan lists: LMS-synced disbursed/sanctioned when present, else requested. */
export function loanPrincipalAmount(loan: Pick<Loan, 'requestedAmount' | 'sanctionedAmount' | 'disbursedAmount'>): number {
  const disbursed = n(loan.disbursedAmount);
  if (disbursed > 0) return disbursed;
  const sanctioned = n(loan.sanctionedAmount);
  if (sanctioned > 0) return sanctioned;
  return n(loan.requestedAmount);
}

/** Total payable including interest/fees when LMS or lifecycle fields are populated. */
export function loanPayableAmount(
  loan: Pick<Loan, 'requestedAmount' | 'sanctionedAmount' | 'disbursedAmount' | 'outstandingAmount' | 'totalRepayable'>,
): number {
  const outstanding = n(loan.outstandingAmount);
  if (outstanding > 0) return outstanding;
  const totalRepayable = n(loan.totalRepayable);
  if (totalRepayable > 0) return totalRepayable;
  return loanPrincipalAmount(loan);
}

/** Interest component when calculated (sanction/disbursement or LMS sync). */
export function loanInterestAmount(
  loan: Pick<
    Loan,
    'requestedAmount' | 'sanctionedAmount' | 'disbursedAmount' | 'outstandingAmount' | 'totalRepayable' | 'interestAmount'
  >,
): number | null {
  const interest = n(loan.interestAmount);
  if (interest > 0) return interest;

  const principal = loanPrincipalAmount(loan);
  const payable = loanPayableAmount(loan);
  const derived = payable - principal;
  return derived > 0 ? derived : null;
}

/** True when Encore account id is present on the loan payload. */
export function loanHasLmsAccount(loan: { lmsAccountId?: string | null; kfsData?: Record<string, unknown> | null }): boolean {
  return Boolean(resolveLmsAccountId(loan));
}

/** Encore LMS account id (e.g. 0000IPP00688) from loan or kfsData. */
export function resolveLmsAccountId(loan: {
  lmsAccountId?: string | null;
  kfsData?: Record<string, unknown> | null;
}): string | null {
  const direct = (loan.lmsAccountId ?? '').trim();
  if (direct) return direct;
  const fromKfs = loan.kfsData?.lmsAccountId;
  if (typeof fromKfs === 'string' && fromKfs.trim()) return fromKfs.trim();
  return null;
}
