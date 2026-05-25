import { useCallback, useEffect, useState } from 'react';
import { ALL_USER_ROLES, authApi, extractApiErrorMessage, useAuth, userApi } from '@plp/shared';
import type { ListedUser, UserRole } from '@plp/shared';

const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';
const filterSelectCls =
  'min-w-[180px] px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none';

const DEFAULT_LENDER_PASSWORD = 'Temp@123';

const LENDER_ROLE_OPTIONS = [
  { value: 'PLATFORM_ADMIN', label: 'Admin' },
  { value: 'CREDIT_MANAGER', label: 'Credit Manager' },
  { value: 'CREDIT_ANALYST', label: 'Credit Officer' },
  { value: 'ACCOUNTS_OFFICER', label: 'Accounts Officer' },
  { value: 'ACCOUNTS_MANAGER', label: 'Accounts Manager' },
  { value: 'COMPLIANCE_OFFICER', label: 'Compliance Officer' },
] as const;

type LenderRole = (typeof LENDER_ROLE_OPTIONS)[number]['value'];

const ENTITY_TYPE_FILTERS = [
  { value: '', label: 'All entity types' },
  { value: 'LENDER', label: 'Lender' },
  { value: 'ANCHOR', label: 'Anchor' },
  { value: 'BORROWER', label: 'Borrower' },
] as const;

function portalEntityType(linkedEntityType: string | null | undefined): string {
  const t = linkedEntityType?.trim();
  if (!t) return 'LENDER';
  if (t === 'ANCHOR') return 'ANCHOR';
  if (t === 'BORROWER') return 'BORROWER';
  return t;
}

function humanizeRole(role: string): string {
  if (role === 'CREDIT_MANAGER') return 'Credit Manager';
  if (role === 'ACCOUNTS_OFFICER') return 'Accounts Officer';
  return role.split('_').join(' ');
}

export default function UsersPage() {
  const { user } = useAuth();
  const isPlatformAdmin = user?.role === 'PLATFORM_ADMIN';

  const [showModal, setShowModal] = useState(false);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState('');
  const [successBanner, setSuccessBanner] = useState('');

  const [users, setUsers] = useState<ListedUser[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [filterRole, setFilterRole] = useState<UserRole | ''>('');
  const [filterEntityType, setFilterEntityType] = useState<'LENDER' | 'ANCHOR' | 'BORROWER' | ''>('');

  const [form, setForm] = useState({
    email: '',
    password: DEFAULT_LENDER_PASSWORD,
    fullName: '',
    role: 'CREDIT_MANAGER' as LenderRole,
  });

  const loadUsers = useCallback(async () => {
    if (!isPlatformAdmin) return;
    setListLoading(true);
    setListError('');
    try {
      const params: Record<string, string> = {};
      if (filterRole) params.role = filterRole;
      if (filterEntityType) params.linkedEntityType = filterEntityType;
      const res = await userApi.list(params);
      const rows = res.data?.data;
      setUsers(Array.isArray(rows) ? (rows as ListedUser[]) : []);
    } catch (e: unknown) {
      setListError(extractApiErrorMessage(e, 'Failed to load users'));
      setUsers([]);
    } finally {
      setListLoading(false);
    }
  }, [isPlatformAdmin, filterRole, filterEntityType]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const openModal = () => {
    setSuccessBanner('');
    setFormError('');
    setForm({
      email: '',
      password: DEFAULT_LENDER_PASSWORD,
      fullName: '',
      role: 'CREDIT_MANAGER',
    });
    setShowModal(true);
  };

  const closeModal = () => {
    if (creating) return;
    setShowModal(false);
    setFormError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setFormError('');
    try {
      await authApi.register({
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        role: form.role,
      });
      const email = form.email.trim();
      const role = form.role;
      const temporaryPassword = form.password;
      setShowModal(false);
      setSuccessBanner(
        `Lender user created successfully — Email: ${email}, Role: ${role}, Temporary password: ${temporaryPassword}`,
      );
      if (isPlatformAdmin) void loadUsers();
    } catch (err: unknown) {
      setFormError(extractApiErrorMessage(err, 'Failed to create lender user'));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div>
      {successBanner && (
        <div className="mb-4 p-3 rounded-lg text-sm bg-emerald-50 text-emerald-800 border border-emerald-200 flex justify-between gap-4 items-start">
          <span>{successBanner}</span>
          <button
            type="button"
            onClick={() => setSuccessBanner('')}
            className="text-emerald-700 hover:text-emerald-900 shrink-0 text-xs font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Users</h1>
          <p className="text-sm text-slate-500 mt-1">
            Create lender accounts and view users across the platform (directory for platform administrators)
          </p>
        </div>
        <button
          type="button"
          onClick={openModal}
          className="inline-flex items-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 shadow-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Create Lender User
        </button>
      </div>

      {isPlatformAdmin ? (
        <div className="space-y-4">
          {listError && (
            <div className="p-3 rounded-lg text-sm bg-red-50 text-red-700 border border-red-200">{listError}</div>
          )}
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Role</label>
              <select
                value={filterRole}
                onChange={(e) => setFilterRole((e.target.value || '') as UserRole | '')}
                className={filterSelectCls}
              >
                <option value="">All roles</option>
                {ALL_USER_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {humanizeRole(r)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Entity type</label>
              <select
                value={filterEntityType}
                onChange={(e) =>
                  setFilterEntityType((e.target.value || '') as '' | 'LENDER' | 'ANCHOR' | 'BORROWER')
                }
                className={filterSelectCls}
              >
                {ENTITY_TYPE_FILTERS.map((opt) => (
                  <option key={opt.value || 'all'} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Email
                  </th>
                  <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Name
                  </th>
                  <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Role
                  </th>
                  <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Entity Type
                  </th>
                  <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Entity ID
                  </th>
                  <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {listLoading ? (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-slate-400 text-sm">
                      Loading users…
                    </td>
                  </tr>
                ) : (
                  users.map((u) => (
                    <tr key={u.id} className="hover:bg-slate-50/80">
                      <td className="px-5 py-3.5 text-slate-800">{u.email}</td>
                      <td className="px-5 py-3.5 text-slate-700">{u.fullName}</td>
                      <td className="px-5 py-3.5">
                        <span className="text-xs font-medium text-slate-700">{humanizeRole(u.role)}</span>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                          {portalEntityType(u.linkedEntityType)}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-600">{u.linkedEntityId || '—'}</td>
                      <td className="px-5 py-3.5 text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${
                            u.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                              : 'bg-slate-50 text-slate-600 ring-1 ring-slate-500/15'
                          }`}
                        >
                          {u.status ?? '—'}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
                {!listLoading && !listError && users.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-slate-400 text-sm">
                      No users match the current filters
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
          Only platform administrators can view the full user directory.
        </div>
      )}

      {showModal && (
        <div
          className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
          role="presentation"
          onClick={(ev) => {
            if (ev.target === ev.currentTarget) closeModal();
          }}
        >
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">Create Lender User</h2>
              <button
                type="button"
                onClick={closeModal}
                disabled={creating}
                className="text-slate-400 hover:text-slate-600 disabled:opacity-50"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6">
              {formError && (
                <div className="mb-4 p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{formError}</div>
              )}
              <div className="space-y-4">
                <div>
                  <label className={labelCls}>Email *</label>
                  <input
                    type="email"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    className={inputCls}
                    placeholder="user@company.com"
                    required
                    disabled={creating}
                  />
                </div>
                <div>
                  <label className={labelCls}>Password</label>
                  <input
                    type="text"
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    className={inputCls}
                    placeholder={DEFAULT_LENDER_PASSWORD}
                    disabled={creating}
                  />
                  <p className="text-xs text-slate-500 mt-1">Temporary password; share securely with the user.</p>
                </div>
                <div>
                  <label className={labelCls}>Full name *</label>
                  <input
                    value={form.fullName}
                    onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                    className={inputCls}
                    placeholder="Full legal name"
                    required
                    disabled={creating}
                  />
                </div>
                <div>
                  <label className={labelCls}>Role *</label>
                  <select
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value as LenderRole })}
                    className={inputCls}
                    disabled={creating}
                  >
                    {LENDER_ROLE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={closeModal}
                  disabled={creating}
                  className="px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50"
                >
                  {creating ? 'Creating…' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
