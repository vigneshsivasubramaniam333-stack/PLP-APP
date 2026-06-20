import { useEffect, useState } from 'react';
import { programApi, loanApi, BtPageHeader, BtCard, BtCardHeader, BtBadge, BtStatCard, useAuth } from '@plp/shared';
import type { Program, Loan } from '@plp/shared';
import { ClearDemoDataButton } from '../components/ClearDemoDataButton';

export default function DashboardPage() {
  const { user } = useAuth();
  const [programs, setPrograms] = useState<Program[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);
  const isPlatformAdmin = user?.role === 'PLATFORM_ADMIN';

  async function fetchData() {
    try {
      const [progRes, loanRes] = await Promise.all([
        programApi.list(),
        loanApi.list(),
      ]);
      setPrograms(progRes.data.data || []);
      setLoans(loanRes.data.data || []);
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading dashboard...</div>
      </div>
    );
  }

  const activePrograms = programs.filter((p) => p.status === 'ACTIVE').length;
  const activeLoans = loans.filter((l) => ['DISBURSED', 'REPAYMENT_DUE'].includes(l.status)).length;
  const overdueLoans = loans.filter((l) => l.status === 'OVERDUE').length;
  const totalDisbursed = loans.filter(l => l.status !== 'REJECTED' && l.status !== 'REQUESTED')
    .reduce((sum, l) => sum + (l.requestedAmount || 0), 0);

  return (
    <div>
      <BtPageHeader
        title="Dashboard"
        description="Overview of your lending operations"
        actions={
          isPlatformAdmin ? (
            <ClearDemoDataButton onCleared={() => void fetchData()} />
          ) : undefined
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <BtStatCard title="Total Programs" value={programs.length.toString()} subtitle={`${activePrograms} active`} accent="orange" />
        <BtStatCard title="Active Loans" value={activeLoans.toString()} subtitle={`of ${loans.length} total`} accent="green" />
        <BtStatCard title="Overdue" value={overdueLoans.toString()} subtitle={overdueLoans > 0 ? 'Needs attention' : 'All clear'} accent={overdueLoans > 0 ? 'red' : 'gray'} />
        <BtStatCard title="Disbursed Value" value={`${(totalDisbursed / 100000).toFixed(1)}L`} subtitle="Total amount" accent="blue" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <BtCard className="overflow-hidden p-0">
          <BtCardHeader title="Programs" actions={<span className="text-xs text-[var(--bt-gray-400)]">{programs.length} total</span>} />
          {programs.length === 0 ? (
            <div className="p-8 text-center">
              <div className="text-[var(--bt-gray-400)] text-sm">No programs created yet</div>
              <p className="text-xs text-[var(--bt-gray-400)] mt-1">Programs will appear here once created</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="bt-table w-full">
                <thead>
                  <tr>
                    <th>Program</th>
                    <th>Product</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {programs.slice(0, 5).map((p) => (
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
                      <td>
                        <BtBadge status={p.status}>{p.status}</BtBadge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </BtCard>

        <BtCard className="overflow-hidden p-0">
          <BtCardHeader title="Recent Loans" actions={<span className="text-xs text-[var(--bt-gray-400)]">{loans.length} total</span>} />
          {loans.length === 0 ? (
            <div className="p-8 text-center">
              <div className="text-[var(--bt-gray-400)] text-sm">No loans yet</div>
              <p className="text-xs text-[var(--bt-gray-400)] mt-1">Loans will appear here once requested</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="bt-table w-full">
                <thead>
                  <tr>
                    <th>Loan</th>
                    <th className="text-right">Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {loans.slice(0, 5).map((l) => (
                    <tr key={l.id}>
                      <td>
                        <div className="font-mono text-xs">{l.loanNumber}</div>
                        <div className="text-xs text-[var(--bt-gray-400)]">{l.productType === 'PAY_DAY_LOAN' ? 'PDL' : 'ID'}</div>
                      </td>
                      <td className="text-right font-medium">{formatCurrency(l.requestedAmount)}</td>
                      <td>
                        <BtBadge status={l.status}>{l.status.replaceAll('_', ' ')}</BtBadge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </BtCard>
      </div>
    </div>
  );
}

function formatCurrency(amount: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
}
