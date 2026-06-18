import { useEffect, useMemo, useState } from 'react';
import {
  programApi,
  subProgramApi,
  useAuth,
  BtPageHeader,
  BtBadge,
  BtCard,
} from '@plp/shared';
import type { Program, SubProgram } from '@plp/shared';

function borrowerIdFromAuth(
  linkedType: string | null | undefined,
  linkedId: string | null | undefined,
): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'BORROWER') return '';
  return (linkedId ?? '').trim();
}

function formatCurrency(amount: number | null | undefined): string {
  if (amount == null || Number.isNaN(Number(amount))) return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
    Number(amount),
  );
}

function formatPct(n: number | null | undefined): string {
  if (n == null || Number.isNaN(Number(n))) return '—';
  return `${Number(n)}%`;
}

function productLabel(productType: string | undefined): string {
  if (productType === 'PAY_DAY_LOAN') return 'Pay Day Loan';
  if (productType === 'INVOICE_DISCOUNTING') return 'Invoice Discounting';
  return productType ?? '—';
}

function flowLabel(flowType: string | undefined): string {
  switch (flowType) {
    case 'PURCHASE_BILL_DISCOUNTING':
      return 'Purchase Bill Discounting';
    case 'SALES_BILL_DISCOUNTING':
      return 'Sales Bill Discounting';
    case 'PAY_LOAN':
    case 'PAY_DAY_LOAN':
      return 'Pay Loan';
    default:
      return flowType ?? '—';
  }
}

type BorrowerMembership = {
  borrowerLimit: number;
  utilizedLimit: number;
  availableLimit: number;
  status: string;
};

type ProgramEnrollment = {
  subProgram: SubProgram;
  program: Program | null;
  membership: BorrowerMembership | null;
};

export default function ProgramsPage() {
  const { user } = useAuth();
  const borrowerId = useMemo(
    () => borrowerIdFromAuth(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [enrollments, setEnrollments] = useState<ProgramEnrollment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!borrowerId) {
      setEnrollments([]);
      setLoading(false);
      return;
    }

    let cancelled = false;

    (async () => {
      setLoading(true);
      setError('');
      try {
        const spRes = await subProgramApi.list();
        const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];

        const programCache = new Map<string, Program>();
        const rows: ProgramEnrollment[] = [];

        for (const sp of subPrograms) {
          let program = programCache.get(sp.programId) ?? null;
          if (!program) {
            try {
              const progRes = await programApi.get(sp.programId);
              program = (progRes.data?.data as Program | undefined) ?? null;
              if (program) programCache.set(sp.programId, program);
            } catch {
              program = null;
            }
          }

          let membership: BorrowerMembership | null = null;
          try {
            const summaryRes = await subProgramApi.getBorrowerLimitSummary(sp.id, borrowerId);
            const data = summaryRes.data?.data as Record<string, unknown> | undefined;
            if (data) {
              membership = {
                borrowerLimit: Number(data.borrowerLimit) || 0,
                utilizedLimit: Number(data.utilizedLimit) || 0,
                availableLimit: Number(data.availableLimit) || 0,
                status: String(data.status ?? 'ACTIVE'),
              };
            }
          } catch {
            membership = null;
          }

          rows.push({ subProgram: sp, program, membership });
        }

        if (!cancelled) setEnrollments(rows);
      } catch {
        if (!cancelled) {
          setError('Could not load your program details. Try again or contact support.');
          setEnrollments([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [borrowerId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading programs...</div>
      </div>
    );
  }

  if (!borrowerId) {
    return (
      <div>
        <BtPageHeader title="Programs" description="Programs and limits you are linked to" />
        <p className="mt-4 bt-alert bt-alert-warning text-sm">
          Your profile is not linked as a borrower. Contact support if this is unexpected.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <BtPageHeader title="Programs" description="Programs and limits you are linked to" />
        <p className="mt-4 bt-alert bt-alert-error text-sm">{error}</p>
      </div>
    );
  }

  return (
    <div>
      <BtPageHeader
        title="Programs"
        description="Program and sub-program details for your linked memberships"
      />

      {enrollments.length === 0 ? (
        <BtCard className="p-10 text-center">
          <p className="text-[var(--bt-gray-500)] text-sm">You are not linked to any programs yet.</p>
          <p className="text-xs text-[var(--bt-gray-400)] mt-1">Contact your employer or anchor to get enrolled.</p>
        </BtCard>
      ) : (
        <div className="space-y-6">
          {enrollments.map(({ subProgram, program, membership }) => (
            <BtCard key={subProgram.id} className="overflow-hidden p-0">
              <div className="px-5 py-4 border-b border-[var(--bt-gray-100)]">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold text-[var(--bt-gray-800)]">
                    {program?.programName ?? 'Program'}
                  </h2>
                  {program ? (
                    <BtBadge tone={program.productType === 'PAY_DAY_LOAN' ? 'blue' : 'gray'}>
                      {productLabel(program.productType)}
                    </BtBadge>
                  ) : null}
                  {program ? <BtBadge status={program.status}>{program.status}</BtBadge> : null}
                </div>
                {program?.programCode ? (
                  <p className="text-xs text-[var(--bt-gray-400)] font-mono mt-0.5">{program.programCode}</p>
                ) : null}
              </div>

              {program ? (
                <div className="px-5 py-4 bg-[var(--bt-gray-50)] border-b border-[var(--bt-gray-100)]">
                  <h3 className="text-xs font-semibold text-[var(--bt-gray-500)] uppercase tracking-wide mb-3">
                    Program details
                  </h3>
                  <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4 text-sm">
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Program limit</div>
                      <div className="font-medium tabular-nums">{formatCurrency(program.programLimit)}</div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Utilized</div>
                      <div className="font-medium tabular-nums text-[var(--bt-amber)]">
                        {formatCurrency(Number(program.utilizedLimit) || 0)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Available</div>
                      <div className="font-medium tabular-nums text-[var(--bt-green)]">
                        {formatCurrency(
                          Number(program.availableLimit) ||
                            Math.max(0, Number(program.programLimit) - (Number(program.utilizedLimit) || 0)),
                        )}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Interest rate</div>
                      <div className="font-medium">{formatPct(program.defaultInterestRate)}</div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Margin</div>
                      <div className="font-medium">{formatPct(program.marginPercent)}</div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Max tenure</div>
                      <div className="font-medium">
                        {program.maxTenureDays != null ? `${program.maxTenureDays} days` : '—'}
                      </div>
                    </div>
                  </div>
                </div>
              ) : null}

              <div className="px-5 py-4 border-b border-[var(--bt-gray-100)]">
                <h3 className="text-xs font-semibold text-[var(--bt-gray-500)] uppercase tracking-wide mb-3">
                  Sub-program
                </h3>
                <div className="flex flex-wrap items-center gap-2 mb-2">
                  <span className="text-sm font-medium text-[var(--bt-gray-800)]">{subProgram.name}</span>
                  <span className="text-xs text-[var(--bt-gray-400)] font-mono">{subProgram.code}</span>
                  <BtBadge status={subProgram.status}>{subProgram.status}</BtBadge>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                  <div>
                    <div className="text-xs text-[var(--bt-gray-500)]">Flow type</div>
                    <div className="font-medium">{flowLabel(subProgram.flowType)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--bt-gray-500)]">Interest rate</div>
                    <div className="font-medium">{formatPct(subProgram.interestRate)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--bt-gray-500)]">Margin</div>
                    <div className="font-medium">{formatPct(subProgram.marginPercent)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-[var(--bt-gray-500)]">Max tenure</div>
                    <div className="font-medium">
                      {subProgram.maxTenureDays != null ? `${subProgram.maxTenureDays} days` : '—'}
                    </div>
                  </div>
                </div>
              </div>

              <div className="px-5 py-4">
                <h3 className="text-xs font-semibold text-[var(--bt-gray-500)] uppercase tracking-wide mb-3">
                  Your limit
                </h3>
                {membership ? (
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Sanctioned limit</div>
                      <div className="font-medium tabular-nums">{formatCurrency(membership.borrowerLimit)}</div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Utilized</div>
                      <div className="font-medium tabular-nums text-[var(--bt-amber)]">
                        {formatCurrency(membership.utilizedLimit)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Available</div>
                      <div className="font-medium tabular-nums text-[var(--bt-green)]">
                        {formatCurrency(
                          membership.availableLimit > 0
                            ? membership.availableLimit
                            : Math.max(0, membership.borrowerLimit - membership.utilizedLimit),
                        )}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-[var(--bt-gray-500)]">Status</div>
                      <div className="mt-0.5">
                        <BtBadge status={membership.status}>{membership.status}</BtBadge>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-[var(--bt-gray-400)]">Limit details unavailable.</p>
                )}
              </div>
            </BtCard>
          ))}
        </div>
      )}
    </div>
  );
}
