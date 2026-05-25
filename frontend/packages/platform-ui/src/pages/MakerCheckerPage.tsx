import { useCallback, useEffect, useState } from 'react';
import {
  anchorApi,
  borrowerApi,
  extractApiErrorMessage,
  loanApi,
  programApi,
  subProgramApi,
  useAuth,
} from '@plp/shared';
import type { Anchor, Borrower, Loan, Program, SubProgram } from '@plp/shared';

type Section = 'programs' | 'subprograms' | 'anchors' | 'borrowers' | 'loans';

/** Viewer: analyst + manager (+ admin). Actor: manager (+ admin). PROGRAM_MANAGER treated as manager per IAM legacy. */
const WORKBENCH_VIEW_ROLES = new Set([
  'PLATFORM_ADMIN',
  'CREDIT_MANAGER',
  'CREDIT_ANALYST',
  'PROGRAM_MANAGER',
]);
const WORKBENCH_ACT_ROLES = new Set(['PLATFORM_ADMIN', 'CREDIT_MANAGER', 'PROGRAM_MANAGER']);

function canViewWorkbench(role: string | undefined): boolean {
  return role != null && WORKBENCH_VIEW_ROLES.has(role);
}

function canApproveWorkbench(role: string | undefined): boolean {
  return role != null && WORKBENCH_ACT_ROLES.has(role);
}

function fmtTs(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

export default function MakerCheckerPage() {
  const { user } = useAuth();
  const role = user?.role;
  const canAct = canApproveWorkbench(role);

  const [section, setSection] = useState<Section>('programs');
  const [loading, setLoading] = useState(true);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [anchors, setAnchors] = useState<Anchor[]>([]);
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [actingKey, setActingKey] = useState<string | null>(null);
  const [successBanner, setSuccessBanner] = useState('');
  const [pageError, setPageError] = useState('');

  const refreshAll = useCallback(async () => {
    setPageError('');
    try {
      const [pr, spr, ar, br, lr] = await Promise.all([
        programApi.list(),
        subProgramApi.list(),
        anchorApi.list(),
        borrowerApi.list(),
        loanApi.list(),
      ]);
      setPrograms((pr.data?.data as Program[]) ?? []);
      setSubPrograms((spr.data?.data as SubProgram[]) ?? []);
      setAnchors((ar.data?.data as Anchor[]) ?? []);
      setBorrowers((br.data?.data as Borrower[]) ?? []);
      setLoans((lr.data?.data as Loan[]) ?? []);
    } catch (e: unknown) {
      setPageError(extractApiErrorMessage(e, 'Failed to load workbench data'));
    }
  }, []);

  useEffect(() => {
    if (!canViewWorkbench(role)) return;
    setLoading(true);
    void refreshAll().finally(() => setLoading(false));
  }, [refreshAll, role]);

  const showSuccess = (msg: string) => {
    setSuccessBanner(msg);
    window.setTimeout(() => setSuccessBanner(''), 6000);
  };

  const runAction = async (key: string, fn: () => Promise<unknown>, okMsg: string) => {
    setActingKey(key);
    try {
      await fn();
      showSuccess(okMsg);
      await refreshAll();
    } catch (e: unknown) {
      setPageError(extractApiErrorMessage(e, 'Action failed'));
    } finally {
      setActingKey(null);
    }
  };

  if (!canViewWorkbench(role)) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-8 text-center max-w-lg mx-auto">
        <h1 className="text-lg font-semibold text-slate-800">Workbench unavailable</h1>
        <p className="text-sm text-slate-600 mt-2">
          Maker–checker queue is only visible to Credit Analyst and Credit Manager roles.
        </p>
      </div>
    );
  }

  const pendingPrograms = programs.filter((p) => p.status === 'DRAFT');
  const pendingSubPrograms = subPrograms.filter((s) => String(s.status).toUpperCase() === 'DRAFT');
  const pendingAnchors = anchors.filter((a) => {
    const st = String(a.status).toUpperCase();
    return st === 'DRAFT' || st === 'UNDER_REVIEW' || st === 'PENDING';
  });
  const pendingBorrowers = borrowers.filter((b) => {
    const st = String(b.status).toUpperCase();
    return st === 'PENDING' || st === 'PENDING_KYC';
  });
  const pendingLoans = loans.filter((l) => l.status === 'REQUESTED');

  const tabs: { id: Section; label: string; count: number }[] = [
    { id: 'programs', label: 'Program approvals', count: pendingPrograms.length },
    { id: 'subprograms', label: 'Sub program approvals', count: pendingSubPrograms.length },
    { id: 'anchors', label: 'Anchor approvals', count: pendingAnchors.length },
    { id: 'borrowers', label: 'Borrower approvals', count: pendingBorrowers.length },
    { id: 'loans', label: 'Loan sanctions', count: pendingLoans.length },
  ];

  const tableCls = 'w-full text-sm border-collapse';
  const thCls =
    'text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider bg-slate-50 border-b border-slate-200';
  const tdCls = 'px-4 py-3 border-b border-slate-100 align-middle';

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Maker–checker workbench</h1>
        <p className="text-sm text-slate-500 mt-1">
          Pending approvals across programs, anchors, borrowers, and loans. Credit Analysts can review;
          Credit Managers can approve or reject.
        </p>
        {!canAct && (
          <p className="text-xs text-amber-700 mt-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 inline-block">
            Your role can view this queue but cannot approve or reject.
          </p>
        )}
      </div>

      {successBanner && (
        <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 flex justify-between gap-3 items-start">
          <span>{successBanner}</span>
          <button type="button" onClick={() => setSuccessBanner('')} className="text-emerald-700 text-xs font-medium shrink-0">
            Dismiss
          </button>
        </div>
      )}

      {pageError && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{pageError}</div>
      )}

      <div className="flex flex-wrap gap-2 mb-4">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setSection(t.id)}
            className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
              section === t.id
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
            }`}
          >
            {t.label}
            <span className={`ml-2 tabular-nums ${section === t.id ? 'text-slate-300' : 'text-slate-400'}`}>
              ({t.count})
            </span>
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        {loading ? (
          <div className="p-12 text-center text-slate-400 text-sm">Loading queue…</div>
        ) : (
          <>
            {section === 'programs' && (
              <table className={tableCls}>
                <thead>
                  <tr>
                    <th className={thCls}>Entity</th>
                    <th className={thCls}>Name / ID</th>
                    <th className={thCls}>Created by</th>
                    <th className={thCls}>Created at</th>
                    <th className={`${thCls} text-right`}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingPrograms.length === 0 ? (
                    <tr>
                      <td colSpan={5} className={`${tdCls} text-slate-400 text-center py-10`}>
                        No programs awaiting approval
                      </td>
                    </tr>
                  ) : (
                    pendingPrograms.map((p) => {
                      const key = `program:${p.id}`;
                      const busy = actingKey === key;
                      return (
                        <tr key={p.id}>
                          <td className={tdCls}>Program</td>
                          <td className={tdCls}>
                            <div className="font-medium text-slate-800">{p.programName}</div>
                            <div className="text-[11px] text-slate-400 font-mono">{p.id}</div>
                          </td>
                          <td className={tdCls}>—</td>
                          <td className={tdCls}>{fmtTs(p.createdAt)}</td>
                          <td className={`${tdCls} text-right whitespace-nowrap`}>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() =>
                                runAction(key, () => programApi.updateStatus(p.id, 'ACTIVE'), 'Program approved')
                              }
                              className="mr-2 px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 rounded-md hover:bg-emerald-700 disabled:opacity-40"
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() => {
                                if (!window.confirm('Reject this program? Status will be set to CLOSED.')) return;
                                void runAction(key, () => programApi.updateStatus(p.id, 'CLOSED'), 'Program rejected');
                              }}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-40"
                            >
                              Reject
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            )}

            {section === 'subprograms' && (
              <table className={tableCls}>
                <thead>
                  <tr>
                    <th className={thCls}>Entity</th>
                    <th className={thCls}>Name / ID</th>
                    <th className={thCls}>Created by</th>
                    <th className={thCls}>Created at</th>
                    <th className={`${thCls} text-right`}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingSubPrograms.length === 0 ? (
                    <tr>
                      <td colSpan={5} className={`${tdCls} text-slate-400 text-center py-10`}>
                        No sub-programs awaiting approval
                      </td>
                    </tr>
                  ) : (
                    pendingSubPrograms.map((s) => {
                      const key = `subprogram:${s.id}`;
                      const busy = actingKey === key;
                      return (
                        <tr key={s.id}>
                          <td className={tdCls}>Sub-program</td>
                          <td className={tdCls}>
                            <div className="font-medium text-slate-800">{s.name}</div>
                            <div className="text-[11px] text-slate-400 font-mono">{s.id}</div>
                          </td>
                          <td className={tdCls}>—</td>
                          <td className={tdCls}>{fmtTs(s.createdAt)}</td>
                          <td className={`${tdCls} text-right whitespace-nowrap`}>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() =>
                                runAction(key, () => subProgramApi.approve(s.id), 'Sub-program approved')
                              }
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 rounded-md hover:bg-emerald-700 disabled:opacity-40"
                            >
                              Approve
                            </button>
                            <p className="text-[10px] text-slate-400 mt-1 max-w-[140px] ml-auto">
                              No reject API — decline via separate process if needed.
                            </p>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            )}

            {section === 'anchors' && (
              <table className={tableCls}>
                <thead>
                  <tr>
                    <th className={thCls}>Entity</th>
                    <th className={thCls}>Name / ID</th>
                    <th className={thCls}>Created by</th>
                    <th className={thCls}>Created at</th>
                    <th className={`${thCls} text-right`}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingAnchors.length === 0 ? (
                    <tr>
                      <td colSpan={5} className={`${tdCls} text-slate-400 text-center py-10`}>
                        No anchors awaiting approval
                      </td>
                    </tr>
                  ) : (
                    pendingAnchors.map((a) => {
                      const key = `anchor:${a.id}`;
                      const busy = actingKey === key;
                      return (
                        <tr key={a.id}>
                          <td className={tdCls}>Anchor</td>
                          <td className={tdCls}>
                            <div className="font-medium text-slate-800">{a.entityName}</div>
                            <div className="text-[11px] text-slate-400 font-mono">{a.id}</div>
                            <div className="text-[10px] text-slate-400">Status: {a.status}</div>
                          </td>
                          <td className={tdCls}>—</td>
                          <td className={tdCls}>{fmtTs(a.createdAt)}</td>
                          <td className={`${tdCls} text-right whitespace-nowrap`}>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() =>
                                runAction(key, () => anchorApi.updateStatus(a.id, 'ACTIVE'), 'Anchor approved')
                              }
                              className="mr-2 px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 rounded-md hover:bg-emerald-700 disabled:opacity-40"
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() => {
                                if (!window.confirm('Reject this anchor? Status will be set to TERMINATED.')) return;
                                void runAction(key, () => anchorApi.updateStatus(a.id, 'TERMINATED'), 'Anchor rejected');
                              }}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-40"
                            >
                              Reject
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            )}

            {section === 'borrowers' && (
              <table className={tableCls}>
                <thead>
                  <tr>
                    <th className={thCls}>Entity</th>
                    <th className={thCls}>Name / ID</th>
                    <th className={thCls}>Created by</th>
                    <th className={thCls}>Created at</th>
                    <th className={`${thCls} text-right`}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingBorrowers.length === 0 ? (
                    <tr>
                      <td colSpan={5} className={`${tdCls} text-slate-400 text-center py-10`}>
                        No borrowers awaiting approval
                      </td>
                    </tr>
                  ) : (
                    pendingBorrowers.map((b) => {
                      const key = `borrower:${b.id}`;
                      const busy = actingKey === key;
                      return (
                        <tr key={b.id}>
                          <td className={tdCls}>Borrower</td>
                          <td className={tdCls}>
                            <div className="font-medium text-slate-800">{b.name}</div>
                            <div className="text-[11px] text-slate-400 font-mono">{b.id}</div>
                            <div className="text-[10px] text-slate-400">Status: {b.status}</div>
                          </td>
                          <td className={tdCls}>—</td>
                          <td className={tdCls}>{fmtTs(b.createdAt)}</td>
                          <td className={`${tdCls} text-right whitespace-nowrap`}>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() =>
                                runAction(key, () => borrowerApi.updateStatus(b.id, 'ACTIVE'), 'Borrower approved')
                              }
                              className="mr-2 px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 rounded-md hover:bg-emerald-700 disabled:opacity-40"
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() => {
                                if (!window.confirm('Reject this borrower? Status will be set to BLACKLISTED.')) return;
                                void runAction(
                                  key,
                                  () => borrowerApi.updateStatus(b.id, 'BLACKLISTED'),
                                  'Borrower rejected',
                                );
                              }}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-40"
                            >
                              Reject
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            )}

            {section === 'loans' && (
              <table className={tableCls}>
                <thead>
                  <tr>
                    <th className={thCls}>Entity</th>
                    <th className={thCls}>Name / ID</th>
                    <th className={thCls}>Created by</th>
                    <th className={thCls}>Created at</th>
                    <th className={`${thCls} text-right`}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingLoans.length === 0 ? (
                    <tr>
                      <td colSpan={5} className={`${tdCls} text-slate-400 text-center py-10`}>
                        No loans awaiting sanction
                      </td>
                    </tr>
                  ) : (
                    pendingLoans.map((loan) => {
                      const key = `loan:${loan.id}`;
                      const busy = actingKey === key;
                      const created = loan.createdAt || loan.requestDate;
                      return (
                        <tr key={loan.id}>
                          <td className={tdCls}>Loan</td>
                          <td className={tdCls}>
                            <div className="font-medium text-slate-800">{loan.loanNumber}</div>
                            <div className="text-[11px] text-slate-400 font-mono">{loan.id}</div>
                            <div className="text-[10px] text-slate-500">
                              ₹{Number(loan.requestedAmount).toLocaleString('en-IN')} requested
                            </div>
                          </td>
                          <td className={tdCls}>—</td>
                          <td className={tdCls}>{fmtTs(created)}</td>
                          <td className={`${tdCls} text-right whitespace-nowrap`}>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() =>
                                runAction(key, () => loanApi.approve(loan.id), 'Loan sanctioned')
                              }
                              className="mr-2 px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 rounded-md hover:bg-emerald-700 disabled:opacity-40"
                            >
                              Sanction
                            </button>
                            <button
                              type="button"
                              disabled={!canAct || busy}
                              onClick={() => {
                                const reason = window.prompt('Rejection reason (required):', '');
                                if (reason == null) return;
                                const t = reason.trim();
                                if (!t) {
                                  window.alert('Reason is required.');
                                  return;
                                }
                                void runAction(key, () => loanApi.reject(loan.id, t), 'Loan rejected');
                              }}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-40"
                            >
                              Reject
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export { canViewWorkbench };
