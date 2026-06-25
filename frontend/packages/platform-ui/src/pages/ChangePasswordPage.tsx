import { type FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth, BrandedAuthFrame, BtButton, BtInput } from '@plp/shared';

export default function ChangePasswordPage() {
  const { user, changePassword, loading, error } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLocalError(null);
    if (newPassword !== confirmPassword) {
      setLocalError('Passwords do not match');
      return;
    }
    try {
      await changePassword(currentPassword, newPassword, confirmPassword);
      navigate('/', { replace: true });
    } catch {
      /* useAuth sets error */
    }
  }

  const displayError = localError ?? error;

  return (
    <BrandedAuthFrame
      title="Set a new password"
      subtitle="Your account was created with a temporary password. Set your own password to continue."
      heroTitle="Program lending platform"
      heroText="Secure your lender portal access before managing programs, borrowers, and loans."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {displayError ? <div className="bt-alert bt-alert-error">{displayError}</div> : null}
        <BtInput
          label="Temporary / current password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
          autoComplete="current-password"
        />
        <BtInput
          label="New password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          autoComplete="new-password"
        />
        <BtInput
          label="Confirm new password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
          autoComplete="new-password"
        />
        <BtButton type="submit" disabled={loading} className="w-full justify-center">
          {loading ? 'Saving…' : 'Set password and continue'}
        </BtButton>
      </form>
      <p className="mt-4 text-center text-xs text-slate-500">
        Use at least 8 characters including a number and a special character.
      </p>
    </BrandedAuthFrame>
  );
}
