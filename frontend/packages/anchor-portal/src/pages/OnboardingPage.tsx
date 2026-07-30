import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '@plp/shared';
import { unwrapApiData } from '../lib/anchorOnboarding';

type OnboardingSummary = {
  onboardingStatus?: string | null;
  applicationId?: string | null;
  applicationNumber?: string | null;
  friendlyStage?: string;
  canResume?: boolean;
  showMyApplication?: boolean;
  sendBackNotes?: string | null;
  requiredActions?: string[];
  losStatus?: string | null;
  status?: string | null;
  forceOnboarding?: boolean;
  menusUnlocked?: boolean;
  requestedAmount?: number | null;
  tenureMonths?: number | null;
  loanProduct?: string | null;
  entityName?: string | null;
};

const primaryBtn =
  'inline-flex items-center justify-center rounded-lg bg-[var(--bt-orange)] px-4 py-2.5 text-sm font-semibold text-white';
const secondaryBtn =
  'inline-flex items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800';

const PIPELINE: { key: string; label: string; match: (s: string | null | undefined, o: string | null | undefined) => boolean }[] = [
  {
    key: 'invite',
    label: 'Invited',
    match: (_s, o) => o === 'INVITED' || o === 'IN_PROGRESS' || o === 'SENT_BACK' || o === 'SUBMITTED' || o === 'COMPLETED',
  },
  {
    key: 'fill',
    label: 'Complete intake',
    match: (_s, o) => o === 'IN_PROGRESS' || o === 'SENT_BACK' || o === 'SUBMITTED' || o === 'COMPLETED',
  },
  {
    key: 'review',
    label: 'Under review',
    match: (s, o) =>
      o === 'SUBMITTED' ||
      o === 'COMPLETED' ||
      ['ANCHOR_SUBMITTED', 'PENDING_CREDIT_OFFICER', 'DOC_VERIFICATION_PENDING', 'KYC_IN_PROGRESS', 'SANCTIONED'].includes(
        (s ?? '').toUpperCase(),
      ),
  },
  {
    key: 'done',
    label: 'Complete',
    match: (s, o) =>
      o === 'COMPLETED' ||
      ['SANCTIONED', 'KFS_GENERATED', 'SANCTION_ISSUED', 'ESIGN_COMPLETED', 'DISBURSED', 'ACTIVE'].includes(
        (s ?? '').toUpperCase(),
      ),
  },
];

export default function OnboardingPage() {
  const [summary, setSummary] = useState<OnboardingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await apiClient.get('/api/v1/portal/anchor/onboarding');
      setSummary(unwrapApiData<OnboardingSummary>(data));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load onboarding status');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <p className="text-sm text-slate-600">Loading onboarding…</p>;
  }
  if (error) {
    return (
      <div>
        <p className="text-sm text-red-600">{error}</p>
        <button type="button" className={`${secondaryBtn} mt-3`} onClick={() => void load()}>
          Retry
        </button>
      </div>
    );
  }

  const losStatus = summary?.losStatus ?? summary?.status ?? null;
  const onboardingStatus = summary?.onboardingStatus ?? null;

  return (
    <div className="w-full">
      <h1 className="text-xl font-semibold text-slate-900">Onboarding</h1>
      <p className="mt-1 text-sm text-slate-600">
        {summary?.friendlyStage ?? 'Complete your anchor application to unlock portal menus.'}
      </p>

      {summary?.applicationNumber || summary?.entityName ? (
        <div className="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
          {summary.entityName ? (
            <p className="font-medium text-slate-900">{summary.entityName}</p>
          ) : null}
          {summary.applicationNumber ? (
            <p className="mt-1 text-slate-700">
              Application: <span className="font-medium">{summary.applicationNumber}</span>
              {losStatus ? ` · ${losStatus}` : null}
            </p>
          ) : null}
        </div>
      ) : null}

      {summary?.sendBackNotes ? (
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <p className="font-medium">Action required</p>
          <p className="mt-1 whitespace-pre-wrap">{summary.sendBackNotes}</p>
        </div>
      ) : null}

      <div className="mt-6">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Progress</p>
        <ol className="space-y-2">
          {PIPELINE.map((stage) => {
            const done = stage.match(losStatus, onboardingStatus);
            return (
              <li
                key={stage.key}
                className={`flex items-center gap-3 rounded-lg border px-3 py-2 text-sm ${
                  done ? 'border-emerald-200 bg-emerald-50 text-emerald-900' : 'border-slate-200 bg-white text-slate-500'
                }`}
              >
                <span
                  className={`inline-flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold ${
                    done ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  {done ? '✓' : ''}
                </span>
                {stage.label}
              </li>
            );
          })}
        </ol>
      </div>

      {summary?.requiredActions && summary.requiredActions.length > 0 ? (
        <div className="mt-6">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Next actions</p>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700">
            {summary.requiredActions.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mt-6 flex flex-wrap gap-3">
        {summary?.canResume ? (
          <Link to="/onboarding/continue" className={primaryBtn}>
            {onboardingStatus === 'SENT_BACK' ? 'Fix & resubmit' : 'Continue application'}
          </Link>
        ) : summary?.applicationId ? (
          <Link to="/onboarding/continue" className={secondaryBtn}>
            View application details
          </Link>
        ) : null}
        {summary?.showMyApplication !== false && summary?.applicationId ? (
          <Link to="/my-application" className={secondaryBtn}>
            My application
          </Link>
        ) : null}
      </div>
    </div>
  );
}
