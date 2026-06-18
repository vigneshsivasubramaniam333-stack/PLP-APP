import { BtCard, BtCardHeader } from './ui/BtCard';

export interface CreditLimitRow {
  label: string;
  limit: number;
  utilized: number;
  available: number;
}

function formatInr(n: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
    Number.isFinite(n) ? n : 0,
  );
}

function utilizationPct(limit: number, utilized: number): number {
  if (!limit || limit <= 0) return 0;
  return Math.min(100, Math.max(0, (utilized / limit) * 100));
}

/** Single dashboard card — each program has Limit / Utilized / Available as three cards side by side. */
export function CreditLimitDashboardSection({
  rows,
  loading,
  title = 'Credit limits',
  subtitle,
}: {
  rows: CreditLimitRow[];
  loading?: boolean;
  title?: string;
  subtitle?: string;
}) {
  if (loading) {
    return (
      <BtCard className="overflow-hidden p-0 mb-8">
        <BtCardHeader title={title} />
        {subtitle ? (
          <p className="px-5 -mt-3 pb-4 text-xs text-[var(--bt-gray-500)]">{subtitle}</p>
        ) : null}
        <div className="px-5 pb-8">
          <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading limit details…</div>
        </div>
      </BtCard>
    );
  }
  if (rows.length === 0) return null;

  return (
    <BtCard className="overflow-hidden p-0 mb-8">
      <BtCardHeader title={title} />
      {subtitle ? (
        <p className="px-5 -mt-3 pb-4 text-xs text-[var(--bt-gray-500)] border-b border-[var(--bt-gray-100)]">
          {subtitle}
        </p>
      ) : (
        <div className="border-b border-[var(--bt-gray-100)]" />
      )}

      <div className="divide-y divide-[var(--bt-gray-100)]">
        {rows.map((row) => (
          <ProgramLimitBlock key={row.label} row={row} />
        ))}
      </div>
    </BtCard>
  );
}

function ProgramLimitBlock({ row }: { row: CreditLimitRow }) {
  const pct = utilizationPct(row.limit, row.utilized);
  const barColor = pct >= 90 ? 'bg-[var(--bt-red)]' : pct >= 70 ? 'bg-[var(--bt-amber)]' : 'bg-[var(--bt-orange)]';
  const badgeClass =
    pct >= 90
      ? 'bt-badge bt-badge-red'
      : pct >= 70
        ? 'bt-badge bt-badge-amber'
        : 'bt-badge bt-badge-green';

  return (
    <div className="px-5 py-5">
      <div className="mb-4">
        <div className="text-sm font-semibold text-[var(--bt-gray-800)] leading-snug" title={row.label}>
          {row.label}
        </div>
        <span className={`inline-flex items-center mt-2 ${badgeClass}`}>
          {pct.toFixed(1)}% utilized
        </span>
        <div className="w-full bg-[var(--bt-gray-100)] rounded-full h-2 mt-3">
          <div className={`h-2 rounded-full transition-all ${barColor}`} style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <LimitMetric title="Limit" value={formatInr(row.limit)} subtitle="Sanctioned credit limit" />
        <LimitMetric title="Utilized" value={formatInr(row.utilized)} subtitle="Amount currently in use" valueClass="orange" />
        <LimitMetric title="Available" value={formatInr(row.available)} subtitle="Remaining headroom" valueClass="green" />
      </div>
    </div>
  );
}

function LimitMetric({
  title,
  value,
  subtitle,
  valueClass,
}: {
  title: string;
  value: string;
  subtitle: string;
  valueClass?: 'orange' | 'green';
}) {
  return (
    <div className="bt-stat min-w-0">
      <div className="bt-stat-label">{title}</div>
      <div className={`bt-stat-value ${valueClass ?? ''}`.trim()}>{value}</div>
      <div className="bt-stat-sub">{subtitle}</div>
    </div>
  );
}
