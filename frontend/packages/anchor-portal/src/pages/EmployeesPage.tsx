import { useState, useEffect, useMemo } from 'react';
import { borrowerApi, salaryApi, useAuth } from '@plp/shared';
import type { Borrower, EmployeeSalaryData } from '@plp/shared';

function anchorIdFromUser(linkedType: string | null | undefined, linkedId: string | null | undefined): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'ANCHOR') return '';
  return (linkedId ?? '').trim();
}

export default function EmployeesPage() {
  const { user } = useAuth();
  const anchorId = useMemo(
    () => anchorIdFromUser(user?.linkedEntityType, user?.linkedEntityId),
    [user?.linkedEntityType, user?.linkedEntityId],
  );

  const [employees, setEmployees] = useState<Borrower[]>([]);
  const [salaryData, setSalaryData] = useState<Record<string, EmployeeSalaryData>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!anchorId) {
      setEmployees([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    borrowerApi
      .list({ anchorId })
      .then((r) => {
        const emps: Borrower[] = r.data.data || [];
        setEmployees(emps);
        emps.forEach((emp) => {
          salaryApi
            .getLatest(emp.id)
            .then((sr) => {
              if (sr.data.data && sr.data.data.id) {
                setSalaryData((prev) => ({ ...prev, [emp.id]: sr.data.data }));
              }
            })
            .catch(() => {});
        });
      })
      .catch((err) => {
        console.error('Failed to fetch employees:', err);
      })
      .finally(() => setLoading(false));
  }, [anchorId]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-48">
        <div className="animate-pulse text-slate-400 text-sm">Loading...</div>
      </div>
    );
  }

  if (!anchorId) {
    return (
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-800">Employees</h1>
          <p className="text-sm text-slate-500 mt-1">Manage employees and view salary eligibility data</p>
        </div>
        <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
          Your session is not linked to an anchor organisation.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Employees</h1>
        <p className="text-sm text-slate-500 mt-1">Borrowers and employees registered under your anchor</p>
      </div>

      {employees.length > 0 ? (
        <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Code</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Name</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Email</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Net Salary</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Accumulated</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Eligible</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {employees.map((emp) => {
                const sd = salaryData[emp.id];
                return (
                  <tr key={emp.id} className="hover:bg-slate-50/80">
                    <td className="px-5 py-3 font-mono text-xs font-medium text-slate-600">{emp.borrowerCode}</td>
                    <td className="px-5 py-3 font-medium text-slate-800">{emp.name}</td>
                    <td className="px-5 py-3 text-xs text-slate-500">{emp.email}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{sd ? formatCurrency(sd.netSalary) : '—'}</td>
                    <td className="px-5 py-3 text-right text-slate-700">{sd ? formatCurrency(sd.accumulatedSalary) : '—'}</td>
                    <td className="px-5 py-3 text-right font-semibold text-emerald-600">{sd ? formatCurrency(sd.eligibleAmount) : '—'}</td>
                    <td className="px-5 py-3 text-center">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${
                          emp.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                            : 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20'
                        }`}
                      >
                        {emp.status}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div className="text-slate-400 text-sm">No employees found for your organisation</div>
        </div>
      )}
    </div>
  );
}
