import { useEffect, useMemo, useState } from 'react';
import {
  borrowerApi,
  programApi,
  subProgramApi,
  anchorApi,
  authApi,
  extractApiErrorMessage,
  getStoredAuthUser,
  lenderLoanCapabilities,
} from '@plp/shared';
import type { Borrower, Program, SubProgram, Anchor } from '@plp/shared';

const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white outline-none';
const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

const DEFAULT_BORROWER_USER_PASSWORD = 'Temp@123';

type MainTab = 'list' | 'onboard' | 'link';

export default function BorrowersPage() {
  const portalCaps = lenderLoanCapabilities(getStoredAuthUser()?.role);
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [anchors, setAnchors] = useState<Anchor[]>([]);
  const [subPrograms, setSubPrograms] = useState<SubProgram[]>([]);
  const [loading, setLoading] = useState(true);
  const [mainTab, setMainTab] = useState<MainTab>('list');

  const [creating, setCreating] = useState(false);
  const [linking, setLinking] = useState(false);
  const [errorOnboard, setErrorOnboard] = useState('');
  const [errorLink, setErrorLink] = useState('');

  const [onboardForm, setOnboardForm] = useState({
    subProgramId: '',
    subProgramBorrowerLimit: '',
    name: '',
    email: '',
    phone: '',
  });

  const [linkForm, setLinkForm] = useState({
    borrowerId: '',
    subProgramId: '',
    borrowerLimit: '',
  });

  const [userBorrower, setUserBorrower] = useState<Borrower | null>(null);
  const [userSubmitting, setUserSubmitting] = useState(false);
  const [userError, setUserError] = useState('');
  const [userSuccess, setUserSuccess] = useState<{ email: string; password: string } | null>(null);
  const [userForm, setUserForm] = useState({
    email: '',
    password: DEFAULT_BORROWER_USER_PASSWORD,
    firstName: '',
    lastName: '',
  });

  const subProgramOptions = useMemo(() => {
    return subPrograms
      .filter((sp) => sp.status === 'ACTIVE')
      .map((sp) => {
        const p = programs.find((x) => x.id === sp.programId);
        const a = anchors.find((x) => x.id === sp.anchorId);
        const label = [
          sp.code,
          sp.name,
          p ? p.programCode : '',
          a ? a.entityName : '',
        ]
          .filter(Boolean)
          .join(' — ');
        return { sp, label };
      });
  }, [subPrograms, programs, anchors]);

  const loadBorrowers = () => {
    borrowerApi
      .list()
      .then((res) => setBorrowers(res.data.data || []))
      .catch(console.error);
  };

  useEffect(() => {
    Promise.all([
      borrowerApi.list().then((res) => setBorrowers(res.data.data || [])),
      programApi.list().then((res) => setPrograms(res.data.data || [])),
      subProgramApi.list().then((res) => setSubPrograms(res.data.data || [])),
      anchorApi.list().then((res) => setAnchors(res.data.data || [])),
    ])
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const openCreateUser = (b: Borrower) => {
    setUserError('');
    setUserSuccess(null);
    setUserBorrower(b);
    setUserForm({
      email: (b.email ?? '').trim(),
      password: DEFAULT_BORROWER_USER_PASSWORD,
      firstName: b.name.trim(),
      lastName: '',
    });
  };

  const closeCreateUser = () => {
    setUserBorrower(null);
    setUserError('');
    setUserSubmitting(false);
  };

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userBorrower) return;
    setUserSubmitting(true);
    setUserError('');
    const fn = userForm.firstName.trim();
    const ln = userForm.lastName.trim();
    const fullName = ln ? `${fn} ${ln}`.trim() : fn;
    if (!userForm.email.trim() || !fullName) {
      setUserError('Email and first name are required');
      setUserSubmitting(false);
      return;
    }
    try {
      await authApi.register({
        email: userForm.email.trim(),
        password: userForm.password,
        fullName,
        role: 'BORROWER',
        linkedEntityId: userBorrower.id,
        linkedEntityType: 'BORROWER',
      });
      setUserSuccess({
        email: userForm.email.trim(),
        password: userForm.password,
      });
      closeCreateUser();
    } catch (err: unknown) {
      setUserError(extractApiErrorMessage(err, 'Failed to create user'));
    } finally {
      setUserSubmitting(false);
    }
  };

  const handleOnboard = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setErrorOnboard('');
    const spId = onboardForm.subProgramId.trim();
    const limit = parseFloat(onboardForm.subProgramBorrowerLimit);
    if (!spId || Number.isNaN(limit) || limit < 0) {
      setErrorOnboard('Select a sub-program and enter a valid limit');
      setCreating(false);
      return;
    }
    try {
      const res = await borrowerApi.create({
        name: onboardForm.name.trim(),
        email: onboardForm.email.trim() || undefined,
        phone: onboardForm.phone.trim() || undefined,
        subProgramId: spId,
        subProgramBorrowerLimit: limit,
        status: 'ACTIVE',
      });
      const created = (res.data as { data?: Borrower }).data;
      loadBorrowers();

      const email = onboardForm.email.trim();
      if (created && email) {
        try {
          await authApi.register({
            email,
            password: DEFAULT_BORROWER_USER_PASSWORD,
            fullName: onboardForm.name.trim(),
            role: 'BORROWER',
            linkedEntityId: created.id,
            linkedEntityType: 'BORROWER',
          });
          setUserSuccess({
            email,
            password: DEFAULT_BORROWER_USER_PASSWORD,
          });
        } catch (regErr: unknown) {
          setErrorOnboard(
            `Borrower created but login was not created: ${extractApiErrorMessage(regErr, 'Registration failed')}. Use "Create User" on the list.`,
          );
          setMainTab('list');
          setCreating(false);
          return;
        }
      }

      setOnboardForm({
        subProgramId: '',
        subProgramBorrowerLimit: '',
        name: '',
        email: '',
        phone: '',
      });
      setMainTab('list');
    } catch (err: unknown) {
      setErrorOnboard(extractApiErrorMessage(err, 'Failed to create borrower'));
    } finally {
      setCreating(false);
    }
  };

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setLinking(true);
    setErrorLink('');
    const bid = linkForm.borrowerId.trim();
    const spId = linkForm.subProgramId.trim();
    const limit = parseFloat(linkForm.borrowerLimit);
    if (!bid || !spId || Number.isNaN(limit) || limit < 0) {
      setErrorLink('Borrower, sub-program, and limit are required');
      setLinking(false);
      return;
    }
    try {
      await subProgramApi.addBorrower(spId, {
        borrowerId: bid,
        borrowerLimit: limit,
        utilizedLimit: 0,
        availableLimit: limit,
        status: 'ACTIVE',
      });
      setLinkForm({ borrowerId: '', subProgramId: '', borrowerLimit: '' });
      setMainTab('list');
    } catch (err: unknown) {
      setErrorLink(extractApiErrorMessage(err, 'Failed to link borrower'));
    } finally {
      setLinking(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-slate-400 text-sm">Loading borrowers...</div>
      </div>
    );
  }

  const tabBtn = (id: MainTab, label: string) => (
    <button
      type="button"
      onClick={() => {
        setMainTab(id);
        setErrorOnboard('');
        setErrorLink('');
      }}
      className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
        mainTab === id ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
      }`}
    >
      {label}
    </button>
  );

  return (
    <div>
      {userSuccess && (
        <div className="mb-4 p-4 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-900 text-sm flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
          <div>
            <p className="font-semibold">Borrower user created successfully</p>
            <p className="mt-1 text-xs text-emerald-800">
              Email: <span className="font-mono">{userSuccess.email}</span>
            </p>
            <p className="mt-0.5 text-xs text-emerald-800">
              Temporary password: <span className="font-mono">{userSuccess.password}</span>
            </p>
            <p className="mt-2 text-[11px] text-emerald-700">Share credentials securely with the borrower.</p>
          </div>
          <button
            type="button"
            onClick={() => setUserSuccess(null)}
            className="shrink-0 text-xs font-semibold text-emerald-800 hover:text-emerald-950 underline"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Borrowers</h1>
          <p className="text-sm text-slate-500 mt-1">Master borrowers and sub-program enrollment</p>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        {tabBtn('list', 'All borrowers')}
        {portalCaps.canCreateProgramArtifacts && tabBtn('onboard', 'Onboard borrower')}
        {portalCaps.canCreateProgramArtifacts && tabBtn('link', 'Link to sub-program')}
      </div>

      {mainTab === 'onboard' && portalCaps.canCreateProgramArtifacts && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-8 max-w-2xl">
          <h2 className="text-lg font-semibold text-slate-800 mb-1">Onboard borrower</h2>
          <p className="text-xs text-slate-500 mb-4">
            Creates the borrower record, enrolls them on the selected sub-program, and creates a portal user when email
            is provided (password {DEFAULT_BORROWER_USER_PASSWORD}).
          </p>
          <form onSubmit={handleOnboard} className="space-y-4">
            <div>
              <label className={labelCls}>Sub-program *</label>
              <select
                required
                value={onboardForm.subProgramId}
                onChange={(e) => setOnboardForm({ ...onboardForm, subProgramId: e.target.value })}
                className={inputCls}
              >
                <option value="">Select sub-program</option>
                {subProgramOptions.map(({ sp, label }) => (
                  <option key={sp.id} value={sp.id}>
                    {label}
                  </option>
                ))}
              </select>
              {subProgramOptions.length === 0 && (
                <p className="text-xs text-amber-600 mt-1">No active sub-programs. Approve a sub-program first.</p>
              )}
            </div>
            <div>
              <label className={labelCls}>Limit on this sub-program *</label>
              <input
                required
                type="number"
                step="0.01"
                min={0}
                value={onboardForm.subProgramBorrowerLimit}
                onChange={(e) => setOnboardForm({ ...onboardForm, subProgramBorrowerLimit: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Name *</label>
              <input
                required
                value={onboardForm.name}
                onChange={(e) => setOnboardForm({ ...onboardForm, name: e.target.value })}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Email</label>
              <input
                type="email"
                value={onboardForm.email}
                onChange={(e) => setOnboardForm({ ...onboardForm, email: e.target.value })}
                className={inputCls}
              />
              <p className="text-[11px] text-slate-500 mt-1">Required to create the first borrower login automatically.</p>
            </div>
            <div>
              <label className={labelCls}>Phone</label>
              <input
                type="tel"
                value={onboardForm.phone}
                onChange={(e) => setOnboardForm({ ...onboardForm, phone: e.target.value })}
                className={inputCls}
              />
            </div>
            {errorOnboard && (
              <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{errorOnboard}</div>
            )}
            <button
              type="submit"
              disabled={creating}
              className="bg-blue-600 text-white px-5 py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
            >
              {creating ? 'Creating…' : 'Create borrower'}
            </button>
          </form>
        </div>
      )}

      {mainTab === 'link' && portalCaps.canCreateProgramArtifacts && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-8 max-w-2xl">
          <h2 className="text-lg font-semibold text-slate-800 mb-1">Link borrower to sub-program</h2>
          <p className="text-xs text-slate-500 mb-4">Attach an existing borrower to an additional sub-program with its own limit.</p>
          <form onSubmit={handleLink} className="space-y-4">
            <div>
              <label className={labelCls}>Borrower *</label>
              <select
                required
                value={linkForm.borrowerId}
                onChange={(e) => setLinkForm({ ...linkForm, borrowerId: e.target.value })}
                className={inputCls}
              >
                <option value="">Select borrower</option>
                {borrowers.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.borrowerCode} — {b.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelCls}>Sub-program *</label>
              <select
                required
                value={linkForm.subProgramId}
                onChange={(e) => setLinkForm({ ...linkForm, subProgramId: e.target.value })}
                className={inputCls}
              >
                <option value="">Select sub-program</option>
                {subProgramOptions.map(({ sp, label }) => (
                  <option key={sp.id} value={sp.id}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelCls}>Limit on this sub-program *</label>
              <input
                required
                type="number"
                step="0.01"
                min={0}
                value={linkForm.borrowerLimit}
                onChange={(e) => setLinkForm({ ...linkForm, borrowerLimit: e.target.value })}
                className={inputCls}
              />
            </div>
            {errorLink && (
              <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{errorLink}</div>
            )}
            <button
              type="submit"
              disabled={linking}
              className="bg-indigo-600 text-white px-5 py-2.5 rounded-lg text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50"
            >
              {linking ? 'Linking…' : 'Link borrower'}
            </button>
          </form>
        </div>
      )}

      {mainTab === 'list' && (
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/80">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
            Borrowers ({borrowers.length})
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">ID</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Name</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Email</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase">Phone</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Status</th>
                <th className="px-5 py-3 text-center text-xs font-semibold text-slate-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {borrowers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-5 py-12 text-center text-slate-400 text-sm">
                    No borrowers yet
                  </td>
                </tr>
              ) : (
                borrowers.map((b) => (
                  <tr key={b.id} className="hover:bg-slate-50/80">
                    <td className="px-5 py-3 font-mono text-xs text-slate-600">{b.id}</td>
                    <td className="px-5 py-3 font-medium text-slate-800">{b.name}</td>
                    <td className="px-5 py-3 text-slate-600">{b.email || '—'}</td>
                    <td className="px-5 py-3 text-slate-600">{b.phone || '—'}</td>
                    <td className="px-5 py-3 text-center">
                      <span className="inline-flex px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                        {b.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-center">
                      <button
                        type="button"
                        onClick={() => openCreateUser(b)}
                        className="text-xs font-semibold text-blue-600 hover:text-blue-800 bg-blue-50 px-3 py-1.5 rounded-lg"
                      >
                        Create User
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
      )}

      {userBorrower && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center">
              <div>
                <h2 className="text-lg font-semibold text-slate-800">Create borrower login</h2>
                <p className="text-xs text-slate-500 mt-0.5 font-mono">{userBorrower.name}</p>
              </div>
              <button
                type="button"
                onClick={closeCreateUser}
                className="text-slate-400 hover:text-slate-600 text-xl leading-none"
              >
                ×
              </button>
            </div>
            <form onSubmit={handleCreateUser} className="p-6 space-y-4">
              <div>
                <label className={labelCls}>Email *</label>
                <input
                  type="email"
                  required
                  value={userForm.email}
                  onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                  className={inputCls}
                  autoComplete="off"
                />
              </div>
              <div>
                <label className={labelCls}>Password *</label>
                <input
                  type="password"
                  required
                  minLength={8}
                  value={userForm.password}
                  onChange={(e) => setUserForm({ ...userForm, password: e.target.value })}
                  className={inputCls}
                  autoComplete="new-password"
                />
                <p className="text-[11px] text-slate-500 mt-1">Default is Temp@123; change before sharing if required.</p>
              </div>
              <div>
                <label className={labelCls}>First name *</label>
                <input
                  required
                  value={userForm.firstName}
                  onChange={(e) => setUserForm({ ...userForm, firstName: e.target.value })}
                  className={inputCls}
                />
              </div>
              <div>
                <label className={labelCls}>Last name</label>
                <input
                  value={userForm.lastName}
                  onChange={(e) => setUserForm({ ...userForm, lastName: e.target.value })}
                  className={inputCls}
                />
              </div>
              {userError && (
                <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{userError}</div>
              )}
              <div className="flex gap-3 pt-2">
                <button
                  type="submit"
                  disabled={userSubmitting}
                  className="flex-1 bg-blue-600 text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
                >
                  {userSubmitting ? 'Creating...' : 'Create User'}
                </button>
                <button
                  type="button"
                  onClick={closeCreateUser}
                  className="px-4 py-2.5 rounded-lg text-sm font-medium text-slate-600 border border-slate-200 hover:bg-slate-50"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
