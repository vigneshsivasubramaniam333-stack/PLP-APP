import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '@plp/shared';
import type { AnchorOnboardingApplication } from '@plp/shared';
import {
  isEditableIntakeStatus,
  loanProductLabel,
  needsResubmit,
  unwrapApiData,
} from '../lib/anchorOnboarding';
import { contactsFromBusinessInfo } from '../lib/anchorContacts';

type Summary = {
  onboardingStatus?: string | null;
  friendlyStage?: string;
  canResume?: boolean;
  losStatus?: string | null;
  status?: string | null;
  applicationNumber?: string | null;
  sendBackNotes?: string | null;
  requiredActions?: string[];
};

type PortalDocument = {
  id?: string;
  documentType?: string | null;
  fileName?: string | null;
  contentType?: string | null;
  createdAt?: string | null;
};

function isPdfContentType(contentType: string | null | undefined): boolean {
  return (contentType ?? '').toLowerCase().includes('pdf');
}

function isImageContentType(contentType: string | null | undefined): boolean {
  return (contentType ?? '').toLowerCase().startsWith('image/');
}

const primaryBtn =
  'inline-flex items-center justify-center rounded-lg bg-[var(--bt-orange)] px-4 py-2.5 text-sm font-semibold text-white';
const secondaryBtn =
  'inline-flex items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800';

function statusTone(status: string | null | undefined): string {
  const s = (status ?? '').toUpperCase();
  if (s.includes('SENT_BACK')) return 'border-amber-200 bg-amber-50 text-amber-950';
  if (s.includes('SUBMITTED') || s.includes('PENDING') || s.includes('VERIFICATION')) {
    return 'border-sky-200 bg-sky-50 text-sky-950';
  }
  if (s.includes('SANCTION') || s.includes('COMPLETE') || s === 'ACTIVE') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-950';
  }
  return 'border-slate-200 bg-slate-50 text-slate-800';
}

export default function MyApplicationPage() {
  const [app, setApp] = useState<AnchorOnboardingApplication | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [docs, setDocs] = useState<PortalDocument[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [docBusyId, setDocBusyId] = useState<string | null>(null);
  const [preview, setPreview] = useState<{
    documentId: string;
    fileName: string;
    contentType: string;
    url: string;
  } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [sumRes, appRes, docsRes] = await Promise.all([
        apiClient.get('/api/v1/portal/anchor/onboarding'),
        apiClient.get('/api/v1/portal/anchor/onboarding/application').catch(() => null),
        apiClient.get('/api/v1/portal/anchor/onboarding/documents').catch(() => ({ data: { data: [] } })),
      ]);
      setSummary(unwrapApiData<Summary>(sumRes.data));
      setApp(appRes ? unwrapApiData<AnchorOnboardingApplication>(appRes.data) : null);
      setDocs(unwrapApiData<PortalDocument[]>(docsRes.data) ?? []);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load application');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    return () => {
      if (preview?.url) URL.revokeObjectURL(preview.url);
    };
  }, [preview]);

  const losStatus = summary?.losStatus ?? summary?.status ?? app?.status ?? null;
  const bi = (app?.businessInfo ?? {}) as Record<string, unknown>;
  const contacts = contactsFromBusinessInfo(bi.contacts);
  const signingAuthorities = contacts.filter((c) => c.isSigningAuthority).length;
  const docsSorted = useMemo(
    () =>
      [...docs].sort((a, b) =>
        String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')),
      ),
    [docs],
  );
  const editable = isEditableIntakeStatus(losStatus);
  const resubmit = needsResubmit(losStatus);
  const notes = summary?.sendBackNotes || app?.anchorSentBackNotes || app?.docVerificationNotes || null;

  async function onDownload(doc: PortalDocument) {
    if (!doc.id) return;
    setError(null);
    setDocBusyId(doc.id);
    try {
      const res = await apiClient.get<Blob>(`/api/v1/portal/anchor/onboarding/documents/${doc.id}/download`, {
        responseType: 'blob',
      });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.fileName || `document-${doc.id}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Download failed');
    } finally {
      setDocBusyId(null);
    }
  }

  async function onPreview(doc: PortalDocument) {
    if (!doc.id) return;
    setError(null);
    setDocBusyId(doc.id);
    try {
      const res = await apiClient.get<Blob>(`/api/v1/portal/anchor/onboarding/documents/${doc.id}/content`, {
        responseType: 'blob',
      });
      const contentType = doc.contentType || res.data.type || 'application/octet-stream';
      const blob = res.data.type === contentType ? res.data : new Blob([res.data], { type: contentType });
      const url = URL.createObjectURL(blob);
      if (preview?.url) URL.revokeObjectURL(preview.url);
      setPreview({
        documentId: doc.id,
        fileName: doc.fileName || `document-${doc.id}`,
        contentType,
        url,
      });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Preview failed');
    } finally {
      setDocBusyId(null);
    }
  }

  function closePreview() {
    if (preview?.url) URL.revokeObjectURL(preview.url);
    setPreview(null);
  }

  if (loading) return <p className="text-sm text-slate-600">Loading…</p>;
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

  return (
    <div className="w-full">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">My application</h1>
          <p className="mt-1 text-sm text-slate-600">
            {summary?.friendlyStage ?? 'Track your anchor onboarding application.'}
          </p>
        </div>
        <button type="button" className={secondaryBtn} onClick={() => void load()}>
          Refresh
        </button>
      </div>

      <div className={`mb-4 rounded-xl border px-4 py-3 text-sm ${statusTone(losStatus)}`}>
        <p className="text-xs font-semibold uppercase tracking-wide opacity-80">Current status</p>
        <p className="mt-1 text-base font-semibold">{losStatus ?? summary?.onboardingStatus ?? '—'}</p>
        {summary?.applicationNumber || app?.applicationNumber ? (
          <p className="mt-1">
            {summary?.applicationNumber ?? app?.applicationNumber}
            {summary?.onboardingStatus ? ` · portal: ${summary.onboardingStatus}` : null}
          </p>
        ) : null}
      </div>

      {notes ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          <p className="font-semibold">Send-back notes</p>
          <p className="mt-1 whitespace-pre-wrap">{notes}</p>
        </div>
      ) : null}

      {summary?.requiredActions && summary.requiredActions.length > 0 ? (
        <div className="mb-4 rounded-xl border border-slate-200 bg-white px-4 py-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">What to do next</p>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
            {summary.requiredActions.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="rounded-xl border border-slate-200 bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Application snapshot</p>
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Entity</dt>
            <dd className="font-medium text-slate-900">{String(bi.corporateName ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Product</dt>
            <dd className="font-medium text-slate-900">
              {loanProductLabel(String(app?.loanProduct ?? 'BUSINESS_WC_INVOICE_DISCOUNTING'))}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Email</dt>
            <dd className="font-medium text-slate-900">{String(bi.email ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Mobile</dt>
            <dd className="font-medium text-slate-900">{String(bi.mobile ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Requested amount</dt>
            <dd className="font-medium text-slate-900">
              {app?.requestedAmount != null ? `₹${Number(app.requestedAmount).toLocaleString('en-IN')}` : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Tenure</dt>
            <dd className="font-medium text-slate-900">
              {app?.tenureMonths != null ? `${app.tenureMonths} months` : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Submitted</dt>
            <dd className="font-medium text-slate-900">
              {app?.submittedAt ? new Date(app.submittedAt).toLocaleString() : 'Not submitted yet'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Last updated</dt>
            <dd className="font-medium text-slate-900">
              {app?.updatedAt ? new Date(app.updatedAt).toLocaleString() : '—'}
            </dd>
          </div>
        </dl>
      </div>

      <div className="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Anchor profile</p>
        {contacts.length > 0 ? (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50">
                  <th className="table-th">User</th>
                  <th className="table-th">Role</th>
                  <th className="table-th">Email</th>
                  <th className="table-th">Mobile</th>
                  <th className="table-th">Signing authority</th>
                  <th className="table-th">Signing order</th>
                </tr>
              </thead>
              <tbody>
                {contacts.map((contact) => (
                  <tr key={contact.id}>
                    <td className="table-td font-medium text-slate-900">{contact.name || '—'}</td>
                    <td className="table-td">{contact.role.replaceAll('_', ' ')}</td>
                    <td className="table-td">{contact.email || '—'}</td>
                    <td className="table-td">{contact.mobile || '—'}</td>
                    <td className="table-td">{contact.isSigningAuthority ? 'Yes' : 'No'}</td>
                    <td className="table-td">
                      {contact.isSigningAuthority
                        ? signingAuthorities > 1
                          ? contact.signingOrder || '—'
                          : 'Primary'
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="mt-3 text-sm text-slate-500">No user details were captured for this application.</p>
        )}
      </div>

      <div className="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Uploaded documents</p>
        {docsSorted.length > 0 ? (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50">
                  <th className="table-th">Document type</th>
                  <th className="table-th">File name</th>
                  <th className="table-th">Uploaded</th>
                  <th className="table-th">Actions</th>
                </tr>
              </thead>
              <tbody>
                {docsSorted.map((doc) => (
                  <tr key={doc.id ?? `${doc.documentType}-${doc.fileName}`}>
                    <td className="table-td">{doc.documentType || '—'}</td>
                    <td className="table-td font-medium text-slate-900">{doc.fileName || '—'}</td>
                    <td className="table-td">
                      {doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '—'}
                    </td>
                    <td className="table-td">
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-800 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                          disabled={!doc.id || docBusyId === doc.id}
                          onClick={() => void onPreview(doc)}
                          title="Preview"
                          aria-label="Preview document"
                        >
                          {docBusyId === doc.id ? (
                            <span className="text-[10px] font-medium">...</span>
                          ) : (
                            <EyeIcon />
                          )}
                        </button>
                        <button
                          type="button"
                          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-800 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                          disabled={!doc.id || docBusyId === doc.id}
                          onClick={() => void onDownload(doc)}
                          title="Download"
                          aria-label="Download document"
                        >
                          <DownloadIcon />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="mt-3 text-sm text-slate-500">No documents uploaded yet.</p>
        )}
      </div>

      <div className="mt-6 flex flex-wrap gap-3">
        {editable ? (
          <Link to="/onboarding/continue" className={primaryBtn}>
            {resubmit ? 'Fix & resubmit' : 'Continue application'}
          </Link>
        ) : (
          <Link to="/onboarding/continue" className={secondaryBtn}>
            View full application
          </Link>
        )}
        <Link to="/onboarding" className={secondaryBtn}>
          Onboarding home
        </Link>
      </div>

      {preview ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="anchor-document-preview-title"
        >
          <div className="w-full max-w-4xl rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between gap-2">
              <h3 id="anchor-document-preview-title" className="text-base font-semibold text-slate-900">
                Document preview
              </h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
                onClick={closePreview}
              >
                Close
              </button>
            </div>
            <p className="mt-2 text-xs text-slate-500">{preview.fileName}</p>
            <div className="mt-3 max-h-[70vh] overflow-auto rounded border border-slate-200 bg-slate-50 p-2">
              {isPdfContentType(preview.contentType) ? (
                <iframe title={preview.fileName} src={preview.url} className="h-[65vh] w-full rounded border-0 bg-white" />
              ) : isImageContentType(preview.contentType) ? (
                <img src={preview.url} alt={preview.fileName} className="mx-auto h-auto max-w-full rounded" />
              ) : (
                <div className="p-4 text-sm text-slate-600">
                  Preview is not available for this file type. Use download instead.
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function EyeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function DownloadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 4v10m0 0-4-4m4 4 4-4M4 20h16"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
