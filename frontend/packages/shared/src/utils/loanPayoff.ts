import { loanApi } from '../api/client';

export interface LoanPayoffInfo {
  payoffAmount: number;
  outstandingAmount: number;
  fromLms: boolean;
}

function n(v: unknown): number {
  const x = Number(v);
  return Number.isFinite(x) ? x : 0;
}

/** Fetches live payoff from LMS when enabled; falls back to stored outstanding. */
export async function fetchLoanPayoff(loanId: string, fallbackOutstanding: number): Promise<LoanPayoffInfo> {
  try {
    const res = await loanApi.getPayoff(loanId);
    const data = res.data?.data as Record<string, unknown> | undefined;
    const payoff = n(data?.payoffAmount);
    const outstanding = n(data?.outstandingAmount) || fallbackOutstanding;
    const payable = payoff > 0 ? payoff : outstanding || fallbackOutstanding;
    return {
      payoffAmount: payable,
      outstandingAmount: outstanding,
      fromLms: Math.abs(payable - fallbackOutstanding) > 0.009,
    };
  } catch {
    return {
      payoffAmount: fallbackOutstanding,
      outstandingAmount: fallbackOutstanding,
      fromLms: false,
    };
  }
}

/** Batch-fetch payoff amounts for multiple loans. */
export async function fetchLoanPayoffs(
  loans: { id: string; outstandingAmount?: number | null }[],
): Promise<Record<string, LoanPayoffInfo>> {
  const entries = await Promise.all(
    loans.map(async (loan) => {
      const fallback = n(loan.outstandingAmount);
      const info = await fetchLoanPayoff(loan.id, fallback);
      return [loan.id, info] as const;
    }),
  );
  return Object.fromEntries(entries);
}

/** Repayment progress using LMS-aware payable amount. */
export function repaymentProgress(totalRepaid: number, payableAmount: number): number {
  const repaid = n(totalRepaid);
  const payable = n(payableAmount);
  const total = repaid + payable;
  if (total <= 0) return repaid > 0 ? 100 : 0;
  return Math.min(100, (repaid / total) * 100);
}
