import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth, PortalLoginBrandHeader, PortalLoginPoweredBy } from '@plp/shared';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login, loading, error } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login(email, password);
      navigate('/');
    } catch {
      // error handled by useAuth
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col">
      <div className="flex-1 flex items-center justify-center p-4">
        <div className="w-full max-w-sm">
          <PortalLoginBrandHeader
            portalSubtitle="Borrower Portal"
            strapline="Request & track your loans"
          />
          <div className="bg-white rounded-2xl shadow-xl p-7">
            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-3 flex items-center gap-2">
                  <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {error}
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Email address</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-sky-500 focus:bg-white outline-none"
                  required />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Password</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-sky-500 focus:bg-white outline-none"
                  required />
              </div>
              <button type="submit" disabled={loading}
                className="w-full bg-sky-600 text-white py-2.5 px-4 rounded-lg text-sm font-semibold hover:bg-sky-700 disabled:opacity-50">
                {loading ? 'Signing in...' : 'Sign in'}
              </button>
            </form>
          </div>
          <PortalLoginPoweredBy className="pb-4" />
        </div>
      </div>
    </div>
  );
}
