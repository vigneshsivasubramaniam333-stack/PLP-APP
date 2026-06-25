import type { InvoicePageMeta } from '../types';

const inputCls = 'bt-input w-full max-w-xs';
const labelCls = 'bt-label';

export type InvoiceListFilters = {
  search: string;
  status: string;
  lifecycle: 'active' | 'closed';
  page: number;
  size: number;
};

type Props = {
  filters: InvoiceListFilters;
  onChange: (next: Partial<InvoiceListFilters>) => void;
  pageMeta?: InvoicePageMeta | null;
  statusOptions?: string[];
};

const DEFAULT_STATUSES = [
  '',
  'UPLOADED',
  'VERIFIED',
  'ELIGIBLE',
  'BORROWER_ACCEPTED',
  'FINANCING_REQUESTED',
  'PARTIALLY_DISCOUNTED',
  'FULLY_DISCOUNTED',
  'REJECTED',
  'CLOSED',
  'EXPIRED',
];

export function InvoiceListToolbar({ filters, onChange, pageMeta, statusOptions = DEFAULT_STATUSES }: Props) {
  const totalPages = pageMeta?.totalPages ?? 0;
  const page = pageMeta?.number ?? filters.page;
  const canPrev = page > 0;
  const canNext = totalPages > 0 && page + 1 < totalPages;

  return (
    <div className="mb-4 space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex gap-2 border-b border-slate-100 pb-3">
        {(['active', 'closed'] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => onChange({ lifecycle: tab, page: 0, status: '' })}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium capitalize ${
              filters.lifecycle === tab
                ? 'bg-[var(--bt-orange)] text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {tab === 'active' ? 'Active' : 'Closed'}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <label className="min-w-[180px] flex-1">
          <span className={labelCls}>Search</span>
          <input
            className={inputCls}
            value={filters.search}
            placeholder="Invoice # …"
            onChange={(e) => onChange({ search: e.target.value, page: 0 })}
          />
        </label>
        <label className="min-w-[160px]">
          <span className={labelCls}>Status</span>
          <select
            className={inputCls}
            value={filters.status}
            onChange={(e) => onChange({ status: e.target.value, page: 0 })}
          >
            {statusOptions.map((s) => (
              <option key={s || 'ALL'} value={s}>
                {s ? s.replace(/_/g, ' ') : 'All statuses'}
              </option>
            ))}
          </select>
        </label>
        <label className="w-28">
          <span className={labelCls}>Page size</span>
          <select
            className={inputCls}
            value={String(filters.size)}
            onChange={(e) => onChange({ size: Number(e.target.value), page: 0 })}
          >
            {[10, 20, 50].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
      </div>
      {pageMeta ? (
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-600">
          <span>
            Showing page {page + 1} of {Math.max(totalPages, 1)} · {pageMeta.totalElements} invoice
            {pageMeta.totalElements === 1 ? '' : 's'}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={!canPrev}
              className="bt-btn bt-btn-secondary bt-btn-sm disabled:opacity-50"
              onClick={() => onChange({ page: page - 1 })}
            >
              Previous
            </button>
            <button
              type="button"
              disabled={!canNext}
              className="bt-btn bt-btn-secondary bt-btn-sm disabled:opacity-50"
              onClick={() => onChange({ page: page + 1 })}
            >
              Next
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
