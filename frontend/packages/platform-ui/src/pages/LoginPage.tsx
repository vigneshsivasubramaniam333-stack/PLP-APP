import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth, BrandedAuthFrame, BtButton, BtInput } from '@plp/shared';

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
    <BrandedAuthFrame
      title="Platform Admin"
      subtitle="Sign in to continue"
      heroTitle="Program lending platform"
      heroText="Manage programs, anchors, borrowers, and loans from one workspace."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error ? <div className="bt-alert bt-alert-error">{error}</div> : null}
        <BtInput label="Email address" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="admin@plp.com" required />
        <BtInput label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <BtButton type="submit" disabled={loading} className="w-full justify-center">
          {loading ? 'Signing in...' : 'Sign in'}
        </BtButton>
      </form>
    </BrandedAuthFrame>
  );
}
