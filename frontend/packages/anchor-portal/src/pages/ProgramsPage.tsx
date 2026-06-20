import { useEffect, useMemo, useState } from 'react';
import {
  portalApi,
  subProgramApi,
  borrowerApi,
  useAuth,
  BtPageHeader,
  BtBadge,
  BtCard,
  BtCardHeader,
  ProgramConfigDetailsPanel,
  buildProgramConfigurationRows,
  buildSubProgramConfigurationRows,
  buildBorrowerTermsRows,
} from '@plp/shared';
import type { Program, SubProgram, SubProgramBorrower, Borrower } from '@plp/shared';

function anchorIdFromUser(linkedType: string | null | undefined, linkedId: string | null | undefined): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'ANCHOR') return '';
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

type SubProgramWithBorrowers = SubProgram & {
  borrowers: SubProgramBorrower[];
  borrowersLoading: boolean;
};

export default function ProgramsPage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgramWithBorrowers[]>([]);
  const [borrowerLookup, setBorrowerLookup] = useState<Map<string, Borrower>>(new Map());
  const [expandedProgramId, setExpandedProgramId] = useState<string | null>(null);
  const [expandedSubProgramId, setExpandedSubProgramId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!anchorId) {
      setPrograms([]);
      setSubPrograms([]);
      setBorrowerLookup(new Map());
      setLoading(false);
      return;
    }

    let cancelled = false;

    (async () => {
      setLoading(true);
      setError('');
      try {
        const [progRes, spRes, borRes] = await Promise.all([
          portalApi.anchorPrograms(anchorId),
          subProgramApi.list(),
          borrowerApi.list({ anchorId }),
        ]);

        if (cancelled) return;

        const progList = (progRes.data?.data as Program[] | undefined) ?? [];
        const spList = (spRes.data?.data as SubProgram[] | undefined) ?? [];
        const borrowers = (borRes.data?.data as Borrower[] | undefined) ?? [];

        const lookup = new Map<string, Borrower>();
        for (const b of borrowers) lookup.set(b.id, b);
        setBorrowerLookup(lookup);
        setPrograms(progList);
        setSubPrograms(
          spList.map((sp) => ({
            ...sp,
            borrowers: [],
            borrowersLoading: false,
          })),
        );
      } catch {
        if (!cancelled) {
          setError('Could not load programs. Try again or contact support.');
          setPrograms([]);
          setSubPrograms([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [anchorId]);

  const subProgramsByProgram = useMemo(() => {
    const map = new Map<string, SubProgramWithBorrowers[]>();
    for (const sp of subPrograms) {
      const list = map.get(sp.programId) ?? [];
      list.push(sp);
      map.set(sp.programId, list);
    }
    return map;
  }, [subPrograms]);

  const loadSubProgramBorrowers = async (subProgramId: string) => {
    setSubPrograms((prev) =>
      prev.map((sp) => (sp.id === subProgramId ? { ...sp, borrowersLoading: true } : sp)),
    );
    try {
      const res = await subProgramApi.listBorrowers(subProgramId);
      const borrowers = (res.data?.data as SubProgramBorrower[] | undefined) ?? [];
      setSubPrograms((prev) =>
        prev.map((sp) =>
          sp.id === subProgramId ? { ...sp, borrowers, borrowersLoading: false } : sp,
        ),
      );
    } catch {
      setSubPrograms((prev) =>
        prev.map((sp) =>
          sp.id === subProgramId ? { ...sp, borrowers: [], borrowersLoading: false } : sp,
        ),
      );
    }
  };

  const toggleSubProgram = (subProgramId: string) => {
    if (expandedSubProgramId === subProgramId) {
      setExpandedSubProgramId(null);
      return;
    }
    setExpandedSubProgramId(subProgramId);
    const sp = subPrograms.find((s) => s.id === subProgramId);
    if (sp && sp.borrowers.length === 0 && !sp.borrowersLoading) {
      void loadSubProgramBorrowers(subProgramId);
    }
  };

  const borrowerName = (borrowerId: string): string => {
    const b = borrowerLookup.get(borrowerId);
    return b?.name?.trim() || b?.borrowerCode?.trim() || borrowerId;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading programs...</div>
      </div>
    );
  }

  if (!anchorId) {
    return (
      <div>
        <BtPageHeader title="Programs" description="Your lending programs and sub-programs" />
        <p className="mt-4 bt-alert bt-alert-warning text-sm">
          Your account is not linked to an anchor organisation. Contact support.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <BtPageHeader title="Programs" description="Your lending programs and sub-programs" />
        <p className="mt-4 bt-alert bt-alert-error text-sm">{error}</p>
      </div>
    );
  }

  return (
    <div>
      <BtPageHeader
        title="Programs"
        description="Programs, sub-programs, and linked borrowers with limit details for your anchor"
      />

      {programs.length === 0 ? (
        <BtCard className="p-10 text-center">
          <p className="text-[var(--bt-gray-500)] text-sm">No programs found for your anchor.</p>
          <p className="text-xs text-[var(--bt-gray-400)] mt-1">Programs are provisioned by your lender.</p>
        </BtCard>
      ) : (
        <div className="space-y-6">
          {programs.map((program) => {
            const programSubPrograms = subProgramsByProgram.get(program.id) ?? [];
            const isExpanded = expandedProgramId === program.id;

            return (
              <BtCard key={program.id} className="overflow-hidden p-0">
                <button
                  type="button"
                  onClick={() => setExpandedProgramId(isExpanded ? null : program.id)}
                  className="w-full text-left px-5 py-4 flex items-start justify-between gap-4 hover:bg-[var(--bt-gray-50)] transition-colors"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-base font-semibold text-[var(--bt-gray-800)]">{program.programName}</h2>
                      <BtBadge tone={program.productType === 'PAY_DAY_LOAN' ? 'blue' : 'gray'}>
                        {productLabel(program.productType)}
                      </BtBadge>
                      <BtBadge status={program.status}>{program.status}</BtBadge>
                    </div>
                    <p className="text-xs text-[var(--bt-gray-400)] font-mono mt-0.5">{program.programCode}</p>
                  </div>
                  <svg
                    className={`h-5 w-5 shrink-0 text-[var(--bt-gray-400)] transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {isExpanded ? (
                  <div className="border-t border-[var(--bt-gray-100)]">
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
                      <ProgramConfigDetailsPanel
                        className="mt-3"
                        rows={buildProgramConfigurationRows(program)}
                        label="Show more program details"
                      />
                    </div>

                    <BtCardHeader
                      title="Sub-programs"
                      actions={
                        <span className="text-xs text-[var(--bt-gray-400)]">{programSubPrograms.length} total</span>
                      }
                    />

                    {programSubPrograms.length === 0 ? (
                      <p className="px-5 pb-5 text-sm text-[var(--bt-gray-400)]">No sub-programs under this program.</p>
                    ) : (
                      <div className="divide-y divide-[var(--bt-gray-100)]">
                        {programSubPrograms.map((sp) => {
                          const spExpanded = expandedSubProgramId === sp.id;
                          return (
                            <div key={sp.id}>
                              <button
                                type="button"
                                onClick={() => toggleSubProgram(sp.id)}
                                className="w-full text-left px-5 py-3 hover:bg-[var(--bt-gray-50)] transition-colors"
                              >
                                <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_auto] gap-x-6 gap-y-2 items-start">
                                  <div className="min-w-0">
                                    <div className="text-sm font-medium text-[var(--bt-gray-800)]">{sp.name}</div>
                                    <div className="text-xs text-[var(--bt-gray-400)] font-mono">{sp.code}</div>
                                    <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--bt-gray-500)]">
                                      <span>Interest: {formatPct(sp.interestRate)}</span>
                                      <span>Margin: {formatPct(sp.marginPercent)}</span>
                                      <span>
                                        Max tenure: {sp.maxTenureDays != null ? `${sp.maxTenureDays} days` : '—'}
                                      </span>
                                    </div>
                                  </div>
                                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs lg:justify-end">
                                    <span className="text-[var(--bt-gray-500)]">{flowLabel(sp.flowType)}</span>
                                    <span className="tabular-nums whitespace-nowrap">
                                      Limit: <strong>{formatCurrency(sp.subProgramLimit)}</strong>
                                    </span>
                                    <span className="tabular-nums whitespace-nowrap text-[var(--bt-amber)]">
                                      Used: {formatCurrency(Number(sp.utilizedLimit) || 0)}
                                    </span>
                                    <span className="tabular-nums whitespace-nowrap text-[var(--bt-green)]">
                                      Avail:{' '}
                                      {formatCurrency(
                                        Number(sp.availableLimit) ||
                                          Math.max(0, Number(sp.subProgramLimit) - (Number(sp.utilizedLimit) || 0)),
                                      )}
                                    </span>
                                    <BtBadge status={sp.status}>{sp.status}</BtBadge>
                                    <svg
                                      className={`h-4 w-4 shrink-0 text-[var(--bt-gray-400)] transition-transform ${spExpanded ? 'rotate-180' : ''}`}
                                      fill="none"
                                      viewBox="0 0 24 24"
                                      stroke="currentColor"
                                      strokeWidth={2}
                                    >
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                                    </svg>
                                  </div>
                                </div>
                              </button>

                              {spExpanded ? (
                                <div className="border-t border-[var(--bt-gray-100)] bg-[var(--bt-gray-50)]/40 px-5 py-4">
                                  <ProgramConfigDetailsPanel
                                    rows={buildSubProgramConfigurationRows(sp)}
                                    label="Show more sub-program details"
                                  />
                                  {sp.borrowersLoading ? (
                                    <div className="text-sm text-[var(--bt-gray-400)] py-4 text-center">
                                      Loading linked borrowers...
                                    </div>
                                  ) : sp.borrowers.length === 0 ? (
                                    <div className="text-sm text-[var(--bt-gray-400)] py-4 text-center">
                                      No borrowers linked to this sub-program.
                                    </div>
                                  ) : (
                                    <div className="overflow-x-auto">
                                      <table className="bt-table w-full">
                                        <thead>
                                          <tr>
                                            <th className="min-w-[180px]">Borrower</th>
                                            <th className="text-right whitespace-nowrap">Limit</th>
                                            <th className="text-right whitespace-nowrap">Utilized</th>
                                            <th className="text-right whitespace-nowrap">Available</th>
                                            <th className="text-center whitespace-nowrap">Status</th>
                                          </tr>
                                        </thead>
                                        <tbody>
                                          {sp.borrowers.map((row) => (
                                            <tr key={row.id}>
                                              <td>
                                                <div className="text-sm font-medium">{borrowerName(row.borrowerId)}</div>
                                                {borrowerLookup.get(row.borrowerId)?.borrowerCode ? (
                                                  <div className="text-xs text-[var(--bt-gray-400)] font-mono">
                                                    {borrowerLookup.get(row.borrowerId)?.borrowerCode}
                                                  </div>
                                                ) : null}
                                                <ProgramConfigDetailsPanel
                                                  className="mt-1.5"
                                                  rows={buildBorrowerTermsRows(row)}
                                                  label="Show borrower terms"
                                                  hideLabel="Hide borrower terms"
                                                />
                                              </td>
                                              <td className="text-right tabular-nums whitespace-nowrap">
                                                {formatCurrency(row.borrowerLimit)}
                                              </td>
                                              <td className="text-right tabular-nums whitespace-nowrap text-[var(--bt-amber)]">
                                                {formatCurrency(row.utilizedLimit)}
                                              </td>
                                              <td className="text-right tabular-nums whitespace-nowrap text-[var(--bt-green)]">
                                                {formatCurrency(row.availableLimit)}
                                              </td>
                                              <td className="text-center whitespace-nowrap">
                                                <BtBadge status={row.status}>{row.status}</BtBadge>
                                              </td>
                                            </tr>
                                          ))}
                                        </tbody>
                                      </table>
                                    </div>
                                  )}
                                </div>
                              ) : null}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ) : null}
              </BtCard>
            );
          })}
        </div>
      )}
    </div>
  );
}
