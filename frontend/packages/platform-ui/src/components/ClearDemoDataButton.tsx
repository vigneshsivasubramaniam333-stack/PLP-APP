import { useEffect, useState } from 'react';
import { getDevResetStatus, resetPlpDemoData } from '@plp/shared';

type ClearDemoDataButtonProps = {
  onCleared: () => void;
  className?: string;
};

export function ClearDemoDataButton({ onCleared, className }: ClearDemoDataButtonProps) {
  const [devResetEnabled, setDevResetEnabled] = useState<boolean | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [resultMsg, setResultMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getDevResetStatus()
      .then((s) => {
        if (!cancelled) setDevResetEnabled(s.devResetEnabled);
      })
      .catch(() => {
        if (!cancelled) setDevResetEnabled(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function confirm() {
    setErr(null);
    setResultMsg(null);
    setBusy(true);
    try {
      await resetPlpDemoData();
      setResultMsg('PLP demo data cleared successfully');
      onCleared();
      setOpen(false);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Request failed');
    } finally {
      setBusy(false);
    }
  }

  const serverHint =
    devResetEnabled === false
      ? 'Server: restart iam, program, and lending services after pulling latest config (PLP_DEV_RESET_ENABLED=true).'
      : devResetEnabled === null
        ? 'Could not verify dev-reset status; reset may fail if not enabled on the server.'
        : null;

  return (
    <>
      {resultMsg ? (
        <div className="bt-alert bt-alert-success mb-2" role="status">
          {resultMsg}
        </div>
      ) : null}
      {serverHint ? (
        <p className="mb-2 max-w-md text-right text-xs text-[var(--bt-amber)]">{serverHint}</p>
      ) : null}
      <button
        type="button"
        disabled={busy}
        onClick={() => {
          setErr(null);
          setResultMsg(null);
          setOpen(true);
        }}
        className={
          className ??
          'bt-btn bt-btn-secondary border-amber-300 bg-[var(--bt-amber-bg)] text-[var(--bt-amber)] hover:bg-[var(--bt-amber-bg)]'
        }
        title="Delete loans, programs, borrowers, and provisioned portal users"
      >
        {busy ? 'Clearing data…' : 'Reset demo data'}
      </button>
      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="reset-demo-title"
        >
          <div className="bt-card w-full max-w-md border-2 border-[var(--bt-amber)] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.18)]">
            <h2 id="reset-demo-title" className="bt-card-title">
              Reset demo data
            </h2>
            <p className="mt-2 text-sm text-[var(--bt-gray-600)]">
              This will permanently delete all loans, programs, anchors, borrowers, invoices, audit
              events, and portal users (except seeded sandbox accounts). This action cannot be undone.
            </p>
            {serverHint ? (
              <p className="mt-2 text-xs text-[var(--bt-amber)]">{serverHint}</p>
            ) : null}
            {err ? <div className="bt-alert bt-alert-error mt-3">{err}</div> : null}
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary"
                onClick={() => setOpen(false)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-danger disabled:opacity-50"
                onClick={() => void confirm()}
                disabled={busy}
              >
                {busy ? 'Clearing data…' : 'Delete all'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
