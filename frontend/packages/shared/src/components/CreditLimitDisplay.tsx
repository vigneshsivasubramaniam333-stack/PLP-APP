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

type MetricAccent = 'slate' | 'amber' | 'emerald';

const cardStyles: Record<MetricAccent, string> = {
  slate: 'bg-slate-50 text-slate-800 ring-slate-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  emerald: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
};

const labelStyles: Record<MetricAccent, string> = {
  slate: 'text-slate-500',
  amber: 'text-amber-600',
  emerald: 'text-emerald-600',
};

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
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden mb-8">
        <div className="px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
          {subtitle ? <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p> : null}
        </div>
        <div className="px-5 py-8">
          <div className="animate-pulse text-slate-400 text-sm">Loading limit details…</div>
        </div>
      </div>
    );
  }
  if (rows.length === 0) return null;

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden mb-8">
      <div className="px-5 py-4 border-b border-slate-100">
        <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
        {subtitle ? <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p> : null}
      </div>

      <div className="divide-y divide-slate-100">
        {rows.map((row) => (
          <ProgramLimitBlock key={row.label} row={row} />
        ))}
      </div>
    </div>
  );
}

function ProgramLimitBlock({ row }: { row: CreditLimitRow }) {
  const pct = utilizationPct(row.limit, row.utilized);
  const barColor = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-amber-500' : 'bg-sky-500';
  const badgeClass =
    pct >= 90
      ? 'bg-red-50 text-red-700 ring-1 ring-red-600/20'
      : pct >= 70
        ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20'
        : 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20';

  return (
    <div className="px-5 py-5">
      <div className="mb-4">
        <div className="text-sm font-semibold text-slate-800 leading-snug" title={row.label}>
          {row.label}
        </div>
        <span className={`inline-flex items-center mt-2 px-2.5 py-1 rounded-md text-xs font-semibold ${badgeClass}`}>
          {pct.toFixed(1)}% utilized
        </span>
        <div className="w-full bg-slate-100 rounded-full h-2 mt-3">
          <div className={`h-2 rounded-full transition-all ${barColor}`} style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div
        className="w-full"
        style={{ display: 'flex', flexDirection: 'row', alignItems: 'stretch', gap: '12px' }}
      >
        <LimitMetricCard
          label="Limit"
          value={formatInr(row.limit)}
          sub="Sanctioned credit limit"
          accent="slate"
        />
        <LimitMetricCard
          label="Utilized"
          value={formatInr(row.utilized)}
          sub="Amount currently in use"
          accent="amber"
        />
        <LimitMetricCard
          label="Available"
          value={formatInr(row.available)}
          sub="Remaining headroom"
          accent="emerald"
        />
      </div>
    </div>
  );
}

function LimitMetricCard({
  label,
  value,
  sub,
  accent,
}: {
  label: string;
  value: string;
  accent: MetricAccent;
  sub: string;
}) {
  return (
    <div
      className={`rounded-xl border shadow-sm p-4 ring-1 flex flex-col min-w-0 ${cardStyles[accent]}`}
      style={{ flex: '1 1 0%', minWidth: 0 }}
    >
      <span className={`text-xs font-medium uppercase tracking-wide ${labelStyles[accent]}`}>{label}</span>
      <span className="text-base sm:text-lg font-bold tabular-nums mt-1 break-words">{value}</span>
      <span className="text-[11px] opacity-70 mt-1 leading-snug">{sub}</span>
    </div>
  );
}
