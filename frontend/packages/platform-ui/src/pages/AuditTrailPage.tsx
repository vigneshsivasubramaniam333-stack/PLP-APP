import { useCallback, useEffect, useState } from 'react';
import {
  auditApi,
  type AuditEventRow,
  type AuditEventsPageBody,
  type AuditListParams,
} from '@plp/shared';

type AuditTab = 'program' | 'lending';

const pageSize = 50;

const TIMELINE_FETCH_SIZE = 100;
const MAX_TIMELINE_PAGES = 30;

const emptyFilters = {
  eventType: '',
  entityType: '',
  status: '',
  performedByRole: '',
  fromDate: '',
  toDate: '',
};

async function fetchAllAuditPagesForEntity(
  listFn: (params: AuditListParams) => ReturnType<typeof auditApi.listProgramAudit>,
  entityType: string,
  entityId: string,
): Promise<AuditEventRow[]> {
  const acc: AuditEventRow[] = [];
  let page = 0;
  let last = false;
  while (!last && page < MAX_TIMELINE_PAGES) {
    const res = await listFn({
      entityType,
      entityId,
      page,
      size: TIMELINE_FETCH_SIZE,
    });
    const body = res.data?.data;
    acc.push(...(body?.content ?? []));
    last = body?.last ?? true;
    page += 1;
  }
  return acc;
}

function mergeTimelineEvents(programRows: AuditEventRow[], lendingRows: AuditEventRow[]): AuditEventRow[] {
  const byId = new Map<string, AuditEventRow>();
  for (const r of [...programRows, ...lendingRows]) {
    byId.set(r.id, r);
  }
  return [...byId.values()].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );
}

export default function AuditTrailPage() {
  const [tab, setTab] = useState<AuditTab>('program');
  const [filters, setFilters] = useState(emptyFilters);
  const [applied, setApplied] = useState(emptyFilters);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<AuditEventRow[]>([]);
  const [pageInfo, setPageInfo] = useState<Omit<AuditEventsPageBody, 'content'> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [timelineOpen, setTimelineOpen] = useState(false);
  const [timelineTarget, setTimelineTarget] = useState<{ entityType: string; entityId: string } | null>(
    null,
  );
  const [timelineEvents, setTimelineEvents] = useState<AuditEventRow[]>([]);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelineError, setTimelineError] = useState<string | null>(null);

  const buildParams = useCallback(
    (p: number, f: typeof emptyFilters): AuditListParams => {
      const params: AuditListParams = { page: p, size: pageSize };
      if (f.eventType.trim()) params.eventType = f.eventType.trim();
      if (f.entityType.trim()) params.entityType = f.entityType.trim();
      if (f.status.trim()) params.status = f.status.trim();
      if (f.performedByRole.trim()) params.performedByRole = f.performedByRole.trim();
      if (f.fromDate.trim()) params.fromDate = f.fromDate.trim();
      if (f.toDate.trim()) params.toDate = f.toDate.trim();
      return params;
    },
    [],
  );

  const fetchPage = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = buildParams(page, applied);
      const res =
        tab === 'program'
          ? await auditApi.listProgramAudit(params)
          : await auditApi.listLendingAudit(params);
      const body = res.data?.data;
      setRows(body?.content ?? []);
      if (body) {
        const { content: _c, ...rest } = body;
        setPageInfo(rest);
      } else {
        setPageInfo(null);
      }
    } catch (e: unknown) {
      setRows([]);
      setPageInfo(null);
      setError(e instanceof Error ? e.message : 'Failed to load audit events');
    } finally {
      setLoading(false);
    }
  }, [applied, buildParams, page, tab]);

  useEffect(() => {
    void fetchPage();
  }, [fetchPage]);

  const closeTimeline = useCallback(() => {
    setTimelineOpen(false);
    setTimelineTarget(null);
    setTimelineEvents([]);
    setTimelineError(null);
    setTimelineLoading(false);
  }, []);

  useEffect(() => {
    if (!timelineOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeTimeline();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [timelineOpen, closeTimeline]);

  useEffect(() => {
    if (!timelineOpen || !timelineTarget) return;
    const { entityType, entityId } = timelineTarget;
    let cancelled = false;
    setTimelineLoading(true);
    setTimelineError(null);
    setTimelineEvents([]);
    void (async () => {
      try {
        const [programRows, lendingRows] = await Promise.all([
          fetchAllAuditPagesForEntity(auditApi.listProgramAudit, entityType, entityId),
          fetchAllAuditPagesForEntity(auditApi.listLendingAudit, entityType, entityId),
        ]);
        if (cancelled) return;
        setTimelineEvents(mergeTimelineEvents(programRows, lendingRows));
      } catch (e: unknown) {
        if (cancelled) return;
        setTimelineError(e instanceof Error ? e.message : 'Failed to load timeline');
      } finally {
        if (!cancelled) setTimelineLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [timelineOpen, timelineTarget]);

  const openTimeline = (entityType: string, entityId: string) => {
    setTimelineTarget({ entityType, entityId });
    setTimelineOpen(true);
  };

  const applyFilters = () => {
    setApplied({ ...filters });
    setPage(0);
  };

  const resetFilters = () => {
    setFilters(emptyFilters);
    setApplied(emptyFilters);
    setPage(0);
  };

  const onTabChange = (next: AuditTab) => {
    setTab(next);
    setPage(0);
  };

  const statusChip = (status: string) => {
    const s = status?.toUpperCase() ?? '';
    const cls =
      s === 'SUCCESS'
        ? 'bg-emerald-50 text-emerald-800 ring-emerald-600/15'
        : s === 'FAILURE'
          ? 'bg-red-50 text-red-700 ring-red-600/15'
          : 'bg-slate-50 text-slate-700 ring-slate-500/15';
    return (
      <span className={`inline-flex px-2 py-0.5 rounded-md text-xs font-semibold ring-1 ${cls}`}>
        {status || '—'}
      </span>
    );
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Audit Trail</h1>
        <p className="text-sm text-slate-500 mt-1">
          Compliance and operations events from program and lending services
        </p>
      </div>

      <div className="flex gap-1 mb-4 p-1 bg-slate-100 rounded-lg w-fit">
        <button
          type="button"
          onClick={() => onTabChange('program')}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            tab === 'program'
              ? 'bg-white text-slate-900 shadow-sm'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Program Audit
        </button>
        <button
          type="button"
          onClick={() => onTabChange('lending')}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            tab === 'lending'
              ? 'bg-white text-slate-900 shadow-sm'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Lending Audit
        </button>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">Event type</span>
            <input
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.eventType}
              onChange={(e) => setFilters((f) => ({ ...f, eventType: e.target.value }))}
              placeholder="e.g. PROGRAM_CREATED"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">Entity type</span>
            <input
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.entityType}
              onChange={(e) => setFilters((f) => ({ ...f, entityType: e.target.value }))}
              placeholder="e.g. LOAN"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">Status</span>
            <input
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.status}
              onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))}
              placeholder="e.g. SUCCESS"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">Role</span>
            <input
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.performedByRole}
              onChange={(e) => setFilters((f) => ({ ...f, performedByRole: e.target.value }))}
              placeholder="Substring match"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">From date</span>
            <input
              type="date"
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.fromDate}
              onChange={(e) => setFilters((f) => ({ ...f, fromDate: e.target.value }))}
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-500">To date</span>
            <input
              type="date"
              className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
              value={filters.toDate}
              onChange={(e) => setFilters((f) => ({ ...f, toDate: e.target.value }))}
            />
          </label>
        </div>
        <div className="flex flex-wrap gap-2 mt-4">
          <button
            type="button"
            onClick={applyFilters}
            className="px-4 py-2 text-sm font-semibold text-white bg-slate-900 rounded-lg hover:bg-slate-800"
          >
            Apply filters
          </button>
          <button
            type="button"
            onClick={resetFilters}
            className="px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
          >
            Reset
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-pulse text-slate-400 text-sm">Loading audit events...</div>
          </div>
        ) : rows.length === 0 ? (
          <div className="flex items-center justify-center h-48">
            <div className="text-center text-slate-400 text-sm">
              No events match your filters
              <p className="text-xs text-slate-400 mt-1">
                Try widening the date range or clearing filters
              </p>
            </div>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm min-w-[960px]">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Time
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Event
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Entity
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Action
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Role
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Status
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider min-w-[180px]">
                      Message
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((row) => (
                    <tr key={row.id} className="hover:bg-slate-50/80 align-top">
                      <td className="px-4 py-3 whitespace-nowrap">
                        <div className="text-xs text-slate-800 font-medium">
                          {new Date(row.createdAt).toLocaleDateString('en-IN', {
                            day: '2-digit',
                            month: 'short',
                            year: 'numeric',
                          })}
                        </div>
                        <div className="text-[11px] text-slate-400 font-mono">
                          {new Date(row.createdAt).toLocaleTimeString('en-IN', {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                          })}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs font-semibold text-slate-800">{row.eventType}</span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="text-xs font-medium text-slate-700">{row.entityType}</div>
                        {row.entityId ? (
                          <button
                            type="button"
                            onClick={() => openTimeline(row.entityType, row.entityId!)}
                            className="mt-0.5 text-left text-[11px] font-mono text-blue-600 hover:text-blue-800 hover:underline break-all max-w-[220px]"
                            title="View approval timeline for this entity"
                          >
                            {row.entityId}
                          </button>
                        ) : (
                          <div className="text-[11px] text-slate-400 font-mono mt-0.5">—</div>
                        )}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className="text-xs text-slate-700">{row.action}</span>
                      </td>
                      <td className="px-4 py-3 max-w-[200px]">
                        <span
                          className="text-xs text-slate-600 break-words line-clamp-3"
                          title={row.performedByRole ?? ''}
                        >
                          {row.performedByRole ?? '—'}
                        </span>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">{statusChip(row.status)}</td>
                      <td className="px-4 py-3 text-xs text-slate-600 max-w-xs">
                        <span className="line-clamp-2" title={row.message ?? ''}>
                          {row.message ?? '—'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="px-4 py-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2 bg-slate-50/50">
              <span className="text-xs text-slate-500">
                Page {(pageInfo?.number ?? page) + 1}
                {pageInfo != null ? ` · ${pageInfo.totalElements} events` : ''}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0 || loading}
                  className="px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-md hover:bg-slate-50 disabled:opacity-40"
                >
                  Previous
                </button>
                <button
                  type="button"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={loading || (pageInfo?.last ?? true)}
                  className="px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-md hover:bg-slate-50 disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Timeline side panel */}
      {timelineOpen && timelineTarget && (
        <>
          <button
            type="button"
            aria-label="Close timeline"
            className="fixed inset-0 z-40 bg-slate-900/40"
            onClick={closeTimeline}
          />
          <aside
            className="fixed inset-y-0 right-0 z-50 w-full max-w-lg bg-white shadow-2xl border-l border-slate-200 flex flex-col"
            role="dialog"
            aria-modal="true"
            aria-labelledby="timeline-title"
          >
            <div className="flex items-start justify-between gap-3 px-5 py-4 border-b border-slate-200 shrink-0">
              <div>
                <h2 id="timeline-title" className="text-lg font-semibold text-slate-900">
                  Timeline
                </h2>
                <p className="text-xs text-slate-500 mt-1 font-mono break-all">
                  {timelineTarget.entityType} · {timelineTarget.entityId}
                </p>
                <p className="text-[11px] text-slate-400 mt-1">
                  Program and lending audit events, oldest first
                </p>
              </div>
              <button
                type="button"
                onClick={closeTimeline}
                className="shrink-0 px-3 py-1.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
              >
                Close
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-4">
              {timelineLoading ? (
                <div className="flex items-center justify-center py-16 text-slate-400 text-sm">
                  Loading timeline…
                </div>
              ) : timelineError ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                  {timelineError}
                </div>
              ) : timelineEvents.length === 0 ? (
                <div className="text-center py-16 text-slate-400 text-sm">
                  No audit events found for this entity in program or lending logs.
                </div>
              ) : (
                <ul className="relative ms-2 border-l border-slate-200 space-y-8 pb-4">
                  {timelineEvents.map((ev) => (
                    <li key={ev.id} className="relative pl-8">
                      <span
                        className="absolute left-0 top-1.5 -translate-x-1/2 h-3 w-3 rounded-full bg-slate-400 ring-4 ring-white"
                        aria-hidden
                      />
                      <div className="text-[11px] text-slate-500 font-mono">
                        {new Date(ev.createdAt).toLocaleString('en-IN', {
                          day: '2-digit',
                          month: 'short',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </div>
                      <div className="mt-1 text-sm font-semibold text-slate-900">{ev.eventType}</div>
                      <div className="mt-1 text-xs text-slate-600">
                        <span className="font-medium text-slate-700">Action:</span> {ev.action}
                      </div>
                      <div className="mt-1 text-xs text-slate-600">
                        <span className="font-medium text-slate-700">Role:</span>{' '}
                        {ev.performedByRole ?? '—'}
                      </div>
                      <div className="mt-1 text-xs text-slate-600 font-mono">
                        <span className="font-medium text-slate-700 font-sans">User:</span>{' '}
                        {ev.performedByUserId ?? '—'}
                      </div>
                      <div className="mt-2">{statusChip(ev.status)}</div>
                      {ev.message ? (
                        <p className="mt-2 text-xs text-slate-600 whitespace-pre-wrap break-words border border-slate-100 rounded-md bg-slate-50/80 px-2 py-1.5">
                          {ev.message}
                        </p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </aside>
        </>
      )}
    </div>
  );
}
