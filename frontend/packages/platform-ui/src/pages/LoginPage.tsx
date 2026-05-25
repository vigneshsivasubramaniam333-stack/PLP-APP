import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth, BRAND_PRIMARY_LOGO_PATH, BRAND_TECH_LOGO_PATH, TECH_LEGAL_NAME } from '@plp/shared';

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
    <div style={{ display: 'flex', minHeight: '100vh' }}>

      {/* Left branding panel */}
      <div style={{
        width: '50%',
        background: '#0F2042',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '48px',
      }}>
        <img
          src={BRAND_PRIMARY_LOGO_PATH}
          alt="Billionloans"
          style={{
            height: '72px',
            width: 'auto',
            display: 'block',
            mixBlendMode: 'screen',
            marginBottom: '24px',
          }}
        />
        <p style={{
          color: 'rgba(255,255,255,0.45)',
          fontSize: '14px',
          fontStyle: 'italic',
          letterSpacing: '0.01em',
          margin: 0,
        }}>
          Powering India's lending ecosystem
        </p>
      </div>

      {/* Right login panel */}
      <div style={{
        width: '50%',
        background: '#F7F8FA',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '48px',
      }}>
        <div style={{ width: '100%', maxWidth: '360px' }}>

          <h1 style={{
            fontSize: '22px',
            fontWeight: '600',
            color: '#0F2042',
            textAlign: 'center',
            margin: '0 0 4px',
          }}>
            Platform Admin
          </h1>
          <p style={{
            fontSize: '13px',
            color: '#94a3b8',
            textAlign: 'center',
            margin: '0 0 32px',
          }}>
            Sign in to continue
          </p>

          <div style={{
            background: '#fff',
            borderRadius: '12px',
            border: '0.5px solid #e2e8f0',
            padding: '28px',
            boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
          }}>
            <form onSubmit={handleSubmit}>
              {error && (
                <div style={{
                  background: '#fef2f2',
                  border: '0.5px solid #fecaca',
                  color: '#dc2626',
                  fontSize: '13px',
                  borderRadius: '8px',
                  padding: '10px 14px',
                  marginBottom: '16px',
                }}>
                  {error}
                </div>
              )}

              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '500', color: '#374151', marginBottom: '6px' }}>
                  Email address
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@plp.com"
                  required
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    fontSize: '13px',
                    border: '0.5px solid #cbd5e1',
                    borderRadius: '8px',
                    background: '#f8fafc',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>

              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '500', color: '#374151', marginBottom: '6px' }}>
                  Password
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    fontSize: '13px',
                    border: '0.5px solid #cbd5e1',
                    borderRadius: '8px',
                    background: '#f8fafc',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                style={{
                  width: '100%',
                  padding: '10px',
                  fontSize: '14px',
                  fontWeight: '600',
                  color: '#fff',
                  background: loading ? '#93c5fd' : '#2563EB',
                  border: 'none',
                  borderRadius: '8px',
                  cursor: loading ? 'not-allowed' : 'pointer',
                }}
              >
                {loading ? 'Signing in...' : 'Sign in'}
              </button>
            </form>
          </div>

          {/* Powered by footer */}
          <div style={{
            marginTop: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
          }}>
            <span style={{ fontSize: '11px', color: '#94a3b8' }}>Powered by</span>
            <img
              src={BRAND_TECH_LOGO_PATH}
              alt={TECH_LEGAL_NAME}
              style={{ height: '11px', width: 'auto', display: 'block' }}
            />
          </div>

        </div>
      </div>
    </div>
  );
}
