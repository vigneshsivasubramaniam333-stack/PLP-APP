import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  anchorApi,
  portalApi,
  useAuth,
  CreditLimitDashboardSection,
  useAnchorCreditLimits,
  BtPageHeader,
  BtBadge,
  BtCard,
  BtCardHeader,
  BtStatCard,
} from '@plp/shared';
import type { Anchor, Program, Invoice, Borrower } from '@plp/shared';

function isAnchorUser(linkedType: string | null | undefined): boolean {
  return (linkedType ?? '').trim().toUpperCase() === 'ANCHOR';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const anchorId =
    isAnchorUser(user?.linkedEntityType) && user?.linkedEntityId?.trim()
      ? user.linkedEntityId.trim()
      : '';

  const [anchorInfo, setAnchorInfo] = useState<Anchor | null>(null);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [employees, setEmployees] = useState<Borrower[]>([]);
  const [salaryRowCount, setSalaryRowCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { rows: limitRows, loading: limitsLoading } = useAnchorCreditLimits(anchorId);

  useEffect(() => {
    if (!anchorId) {
      setLoading(false);
      setPrograms([]);
      setInvoices([]);
      setEmployees([]);
      setSalaryRowCount(null);
      setAnchorInfo(null);
      setError('');
      return;
    }

    let cancelled = false;
    const payPeriod = new Date().toISOString().slice(0, 7);

    (async () => {
      setLoading(true);
      setError('');
      try {
        const [progRes, invRes, empRes, anchorRes] = await Promise.all([
          portalApi.anchorPrograms(anchorId),
          portalApi.anchorInvoices(anchorId),
          portalApi.anchorEmployees(anchorId),
          anchorApi.get(anchorId),
        ]);

        let salaryCount: number | null = null;
        try {
          const salRes = await portalApi.anchorSalary(anchorId, payPeriod);
          const payload = salRes.data ?? salRes;
          const rows = Array.isArray(payload) ? payload : (payload as { data?: unknown })?.data ?? [];
          salaryCount = Array.isArray(rows) ? rows.length : 0;
        } catch {
          salaryCount = null;
        }

        if (cancelled) return;

        setPrograms(progRes.data?.data ?? []);
        setInvoices(invRes.data?.data ?? []);
        setEmployees(empRes.data?.data ?? []);
        setSalaryRowCount(salaryCount);
        const a = anchorRes.data?.data as Anchor | undefined;
        setAnchorInfo(a ?? null);
      } catch (err) {
        console.error('Failed to load dashboard data:', err);
        if (!cancelled) {
          setError('Could not load your dashboard. Try signing in again or contact support.');
          setPrograms([]);
          setInvoices([]);
          setEmployees([]);
          setSalaryRowCount(null);
          setAnchorInfo(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [anchorId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading dashboard...</div>
      </div>
    );
  }

  if (!anchorId) {
    return (
      <div>
        <BtPageHeader title="Anchor Dashboard" description="Overview for your organisation" />
        <p className="mt-4 bt-alert bt-alert-warning text-sm">
          Your account is not linked to an anchor organisation. Contact support.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <BtPageHeader title="Anchor Dashboard" description="Overview for your organisation" />
        <p className="mt-4 bt-alert bt-alert-error text-sm">{error}</p>
      </div>
    );
  }

  const pdlPrograms = programs.filter((p) => p.productType === 'PAY_DAY_LOAN');
  const idPrograms = programs.filter((p) => p.productType === 'INVOICE_DISCOUNTING');
  const payPeriod = new Date().toISOString().slice(0, 7);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  const recentInvoices = [...invoices].slice(0, 8);

  return (
    <div>
      <BtPageHeader
        title="Anchor Dashboard"
        description={`Overview for ${anchorInfo?.entityName ?? anchorInfo?.anchorCode ?? 'your organisation'}`}
      />

      <CreditLimitDashboardSection
        rows={limitRows}
        loading={limitsLoading}
        title="Program credit limits"
        subtitle="Limit, utilized, and available headroom for your programs and sub-programs"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <BtStatCard title="Programs" value={String(programs.length)} subtitle="Your anchor programs" accent="orange" />
        <BtStatCard title="Employees" value={String(employees.length)} subtitle="Linked borrowers" accent="green" />
        <BtStatCard title="Invoices" value={String(invoices.length)} subtitle="Total submitted" accent="blue" />
        <BtStatCard
          title="Salary rows"
          value={salaryRowCount === null ? '—' : String(salaryRowCount)}
          subtitle={`Current period (${payPeriod})`}
          accent="amber"
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <BtStatCard title="Pay Day Loan" value={String(pdlPrograms.length)} subtitle="PDL programs" accent="orange" />
        <BtStatCard title="Invoice Discounting" value={String(idPrograms.length)} subtitle="ID programs" accent="purple" />
      </div>

      <BtCard className="overflow-hidden p-0 mb-8">
        <BtCardHeader
          title="Your programs"
          actions={<span className="text-xs text-[var(--bt-gray-400)]">{programs.length} total</span>}
        />
        <p className="px-5 -mt-3 pb-4 text-xs text-[var(--bt-gray-500)] border-b border-[var(--bt-gray-100)]">
          Tenant-scoped to your linked anchor only
        </p>
        {programs.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="bt-table w-full">
              <thead>
                <tr>
                  <th>Program</th>
                  <th>Product</th>
                  <th className="text-right">Limit</th>
                  <th className="text-right">Utilized</th>
                  <th className="text-right">Available</th>
                  <th className="text-center">Status</th>
                </tr>
              </thead>
              <tbody>
                {programs.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <div className="font-medium text-sm">{p.programName}</div>
                      <div className="text-xs text-[var(--bt-gray-400)] font-mono">{p.programCode}</div>
                    </td>
                    <td>
                      <BtBadge tone={p.productType === 'PAY_DAY_LOAN' ? 'blue' : 'gray'}>
                        {p.productType === 'PAY_DAY_LOAN' ? 'Pay Day Loan' : 'Invoice Discounting'}
                      </BtBadge>
                    </td>
                    <td className="text-right font-medium tabular-nums">{formatCurrency(p.programLimit)}</td>
                    <td className="text-right font-medium tabular-nums text-[var(--bt-amber)]">
                      {formatCurrency(Number(p.utilizedLimit) || 0)}
                    </td>
                    <td className="text-right font-medium tabular-nums text-[var(--bt-green)]">
                      {formatCurrency(
                        Number(p.availableLimit) || Math.max(0, Number(p.programLimit) - (Number(p.utilizedLimit) || 0)),
                      )}
                    </td>
                    <td className="text-center">
                      <BtBadge status={p.status}>{p.status}</BtBadge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-12 text-center">
            <div className="text-[var(--bt-gray-400)] text-sm">No programs found for your anchor</div>
            <p className="text-xs text-[var(--bt-gray-400)] mt-1">Programs are provisioned by your lender</p>
          </div>
        )}
      </BtCard>

      <BtCard className="overflow-hidden p-0">
        <BtCardHeader
          title="Recent invoices"
          actions={
            <div className="flex items-center gap-3">
              <span className="text-xs text-[var(--bt-gray-400)]">{invoices.length} total</span>
              <Link to="/invoices" className="text-xs font-semibold text-[var(--bt-orange)] hover:underline">
                View all →
              </Link>
            </div>
          }
        />
        <p className="px-5 -mt-3 pb-4 text-xs text-[var(--bt-gray-500)] border-b border-[var(--bt-gray-100)]">
          Latest rows for your anchor (up to 8)
        </p>
        {recentInvoices.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="bt-table w-full">
              <thead>
                <tr>
                  <th>Invoice #</th>
                  <th className="text-right">Net</th>
                  <th className="text-center">Status</th>
                </tr>
              </thead>
              <tbody>
                {recentInvoices.map((inv) => (
                  <tr key={inv.id}>
                    <td className="font-mono text-xs font-medium">{inv.invoiceNumber}</td>
                    <td className="text-right tabular-nums">{formatCurrency(inv.netAmount)}</td>
                    <td className="text-center">
                      <BtBadge status={inv.status}>{inv.status}</BtBadge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-10 text-center text-[var(--bt-gray-400)] text-sm">No invoices yet</div>
        )}
      </BtCard>
    </div>
  );
}
