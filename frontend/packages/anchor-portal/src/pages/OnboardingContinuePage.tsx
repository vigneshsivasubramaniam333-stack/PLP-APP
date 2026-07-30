import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiClient } from '@plp/shared';
import type { AnchorOnboardingApplication } from '@plp/shared';
import { AnchorContactsUsersSection } from '../components/AnchorContactsUsersSection';
import {
  primaryContactFromCorporate,
  resolvePortalContactsConfig,
  validateAnchorContacts,
} from '../lib/anchorContacts';
import {
  buildPortalSavePayload,
  docsByType,
  hydrateFormFromApplication,
  isEditableIntakeStatus,
  loanProductLabel,
  missingRequiredDocs,
  needsResubmit,
  parsePortalIdentitySchema,
  resolveAllowedStatesFromWorkflow,
  resolveOnboardingStepLabels,
  resolvePortalDocumentSlots,
  unwrapApiData,
  validateConsent,
  validateCorporate,
  validateIdentity,
  type AnchorPortalFormState,
  type GeoCityOption,
  type GeoStateOption,
  type PortalDocItem,
  type PortalWorkflowSnapshot,
} from '../lib/anchorOnboarding';

const fieldLabel = 'block text-xs font-medium text-slate-600 mb-1';
const fieldInput =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-1 focus:ring-[var(--bt-orange)] disabled:bg-slate-50 disabled:text-slate-500';
const primaryBtn =
  'inline-flex items-center justify-center rounded-lg bg-[var(--bt-orange)] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50';
const secondaryBtn =
  'inline-flex items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800 disabled:opacity-50';

function Stepper({ step, labels }: { step: number; labels: readonly string[] }) {
  return (
    <ol className="mb-6 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {labels.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={`inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold ${
              i === step
                ? 'bg-[var(--bt-orange)] text-white'
                : i < step
                  ? 'bg-emerald-100 text-emerald-800'
                  : 'bg-slate-100 text-slate-500'
            }`}
          >
            {i + 1}
          </span>
          <span className={i === step ? 'font-semibold text-slate-900' : 'text-slate-500'}>{label}</span>
          {i < labels.length - 1 ? <span className="text-slate-300">/</span> : null}
        </li>
      ))}
    </ol>
  );
}

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className={fieldLabel}>{label}</p>
      <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">
        {value || '—'}
      </p>
    </div>
  );
}

function apiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object' && 'response' in e) {
    const res = (e as { response?: { data?: { message?: string; error?: string }; status?: number } }).response;
    const msg = res?.data?.message || res?.data?.error;
    if (msg) return msg;
  }
  return e instanceof Error ? e.message : fallback;
}

export default function OnboardingContinuePage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [app, setApp] = useState<AnchorOnboardingApplication | null>(null);
  const [workflow, setWorkflow] = useState<PortalWorkflowSnapshot | null>(null);
  const [form, setForm] = useState<AnchorPortalFormState>(() => hydrateFormFromApplication(null));
  const [docs, setDocs] = useState<PortalDocItem[]>([]);
  const [geoStates, setGeoStates] = useState<GeoStateOption[]>([]);
  const [cities, setCities] = useState<GeoCityOption[]>([]);
  const [loadingCities, setLoadingCities] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [uploadingType, setUploadingType] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [geoWarning, setGeoWarning] = useState<string | null>(null);
  const [workflowWarning, setWorkflowWarning] = useState<string | null>(null);

  const editable = isEditableIntakeStatus(app?.status);
  const resubmit = needsResubmit(app?.status);
  const byType = useMemo(() => docsByType(docs), [docs]);
  const sendBackNotes = app?.anchorSentBackNotes || app?.docVerificationNotes || null;
  const identityFields = useMemo(
    () => parsePortalIdentitySchema(workflow, form.borrowerType || 'COMPANY'),
    [workflow, form.borrowerType],
  );
  const docSlots = useMemo(() => resolvePortalDocumentSlots(workflow), [workflow]);
  const allowedStates = useMemo(() => resolveAllowedStatesFromWorkflow(workflow), [workflow]);
  const contactsCfg = useMemo(() => resolvePortalContactsConfig(workflow), [workflow]);
  const contactsEnabled = contactsCfg.enabled;
  const stepLabels = useMemo(() => resolveOnboardingStepLabels(contactsEnabled), [contactsEnabled]);
  const reviewStep = contactsEnabled ? 6 : 5;
  const usersStep = contactsEnabled ? 5 : -1;

  useEffect(() => {
    if (!contactsEnabled) return;
    setForm((f) => {
      const corpName = (f.accountHolderName || f.corporateName).trim();
      const corpEmail = f.email.trim();
      const corpMobile = f.mobile.replace(/\D/g, '').slice(0, 12);
      if (f.contacts.length === 0) {
        if (!corpEmail && !corpMobile) return f;
        return {
          ...f,
          contacts: [
            primaryContactFromCorporate({
              name: corpName,
              email: corpEmail,
              mobile: corpMobile,
            }),
          ],
        };
      }
      const primary = f.contacts[0]!;
      const primaryEmail = primary.email.trim();
      const emailStale =
        !!corpEmail &&
        (!primaryEmail ||
          (corpEmail.toLowerCase().startsWith(primaryEmail.toLowerCase()) &&
            primaryEmail.toLowerCase() !== corpEmail.toLowerCase()));
      const mobileStale =
        !!corpMobile &&
        (!primary.mobile.trim() ||
          (corpMobile.startsWith(primary.mobile.replace(/\D/g, '')) &&
            primary.mobile.replace(/\D/g, '') !== corpMobile));
      const nameStale = !!corpName && !primary.name.trim();
      if (!emailStale && !mobileStale && !nameStale) return f;
      const next = f.contacts.slice();
      next[0] = {
        ...primary,
        name: nameStale || emailStale ? corpName || primary.name : primary.name,
        email: emailStale ? corpEmail : primary.email,
        mobile: mobileStale ? corpMobile : primary.mobile,
      };
      return { ...f, contacts: next };
    });
  }, [contactsEnabled, form.email, form.mobile, form.corporateName, form.accountHolderName]);

  const filteredStates = useMemo(() => {
    if (!allowedStates || allowedStates.length === 0) return geoStates;
    const allow = new Set(allowedStates.map((s) => s.toLowerCase()));
    return geoStates.filter((s) => allow.has(s.stateName.toLowerCase()));
  }, [geoStates, allowedStates]);

  const matchedState = useMemo(() => {
    const k = form.state.trim().toLowerCase();
    if (!k) return undefined;
    return geoStates.find((s) => s.stateName.toLowerCase() === k);
  }, [geoStates, form.state]);

  const patch = useCallback((partial: Partial<AnchorPortalFormState>) => {
    setForm((prev) => ({ ...prev, ...partial }));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setGeoWarning(null);
    setWorkflowWarning(null);
    try {
      const [ctxOutcome, docOutcome, statesOutcome] = await Promise.all([
        apiClient
          .get('/api/v1/portal/anchor/onboarding/intake-context')
          .then((res) => ({ ok: true as const, res }))
          .catch((e: unknown) => ({ ok: false as const, error: e })),
        apiClient
          .get('/api/v1/portal/anchor/onboarding/documents')
          .then((res) => ({ ok: true as const, res }))
          .catch(() => ({ ok: false as const, res: null })),
        apiClient
          .get('/api/v1/portal/anchor/onboarding/geo/states')
          .then((res) => ({ ok: true as const, res }))
          .catch((e: unknown) => ({ ok: false as const, error: e })),
      ]);

      let application: AnchorOnboardingApplication | null = null;
      let wf: PortalWorkflowSnapshot | null = null;
      if (ctxOutcome.ok) {
        const ctx = unwrapApiData<{ application?: AnchorOnboardingApplication; workflow?: PortalWorkflowSnapshot }>(
          ctxOutcome.res.data,
        );
        application = ctx?.application ?? null;
        wf = ctx?.workflow ?? null;
        if (!wf) {
          setWorkflowWarning(
            'Active ANCHOR workflow was not returned. Users, KYC fields, and workflow documents may be incomplete until intake-context succeeds.',
          );
        }
      } else {
        setWorkflowWarning(
          `Could not load intake context (${apiErrorMessage(ctxOutcome.error, 'request failed')}). Users and workflow-driven documents will not appear until this is fixed.`,
        );
      }
      if (!application) {
        const appRes = await apiClient.get('/api/v1/portal/anchor/onboarding/application');
        application = unwrapApiData<AnchorOnboardingApplication>(appRes.data);
      }

      const documentList = docOutcome.ok
        ? (unwrapApiData<PortalDocItem[]>(docOutcome.res.data) ?? [])
        : [];
      let states: GeoStateOption[] = [];
      if (statesOutcome.ok) {
        states = unwrapApiData<GeoStateOption[]>(statesOutcome.res.data) ?? [];
        if (!Array.isArray(states) || states.length === 0) {
          setGeoWarning('States list came back empty from the server.');
        }
      } else {
        setGeoWarning(
          `States unavailable (${apiErrorMessage(statesOutcome.error, 'geo request failed')}). Restart program-service from the staging build with the local profile.`,
        );
      }
      setApp(application);
      setWorkflow(wf);
      setForm(hydrateFormFromApplication(application));
      setDocs(Array.isArray(documentList) ? documentList : []);
      setGeoStates(Array.isArray(states) ? states : []);
    } catch (e: unknown) {
      setError(apiErrorMessage(e, 'Failed to load application'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!matchedState?.id) {
      setCities([]);
      return;
    }
    let cancelled = false;
    setLoadingCities(true);
    void apiClient
      .get(`/api/v1/portal/anchor/onboarding/geo/states/${matchedState.id}/cities`)
      .then((res) => {
        if (cancelled) return;
        const list = unwrapApiData<GeoCityOption[]>(res.data) ?? [];
        setCities(Array.isArray(list) ? list : []);
      })
      .catch(() => {
        if (!cancelled) setCities([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingCities(false);
      });
    return () => {
      cancelled = true;
    };
  }, [matchedState?.id]);

  async function saveSection(): Promise<boolean> {
    setBusy(true);
    setError(null);
    try {
      const { data } = await apiClient.put(
        '/api/v1/portal/anchor/onboarding/application',
        buildPortalSavePayload(form),
      );
      const updated = unwrapApiData<AnchorOnboardingApplication>(data);
      setApp(updated);
      setForm(hydrateFormFromApplication(updated));
      return true;
    } catch (e: unknown) {
      setError(apiErrorMessage(e, 'Save failed'));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function onNext() {
    if (!editable && step < reviewStep) {
      setStep((s) => Math.min(reviewStep, s + 1));
      return;
    }
    if (step === 1) {
      const msg = validateCorporate(form);
      if (msg) {
        setError(msg);
        return;
      }
      if (!(await saveSection())) return;
    } else if (step === 2) {
      const missing = missingRequiredDocs(byType, docSlots);
      if (missing.length > 0 && editable) {
        setError(`Please upload: ${missing.join(', ')}`);
        return;
      }
    } else if (step === 3) {
      const msg = validateIdentity(form, identityFields);
      if (msg) {
        setError(msg);
        return;
      }
      if (!(await saveSection())) return;
    } else if (step === 4) {
      const msg = validateConsent(form);
      if (msg) {
        setError(msg);
        return;
      }
      if (!(await saveSection())) return;
      if (contactsEnabled) {
        setForm((f) => {
          if (f.contacts.length > 0) return f;
          return {
            ...f,
            contacts: [
              primaryContactFromCorporate({
                name: f.accountHolderName || f.corporateName,
                email: f.email,
                mobile: f.mobile,
              }),
            ],
          };
        });
      }
    } else if (contactsEnabled && step === usersStep) {
      const msg = validateAnchorContacts(form.contacts, contactsCfg.maxUsers);
      if (msg) {
        setError(msg);
        return;
      }
      if (!(await saveSection())) return;
    }
    setError(null);
    setStep((s) => Math.min(reviewStep, s + 1));
  }

  async function onSubmit() {
    if (!editable) return;
    const corp = validateCorporate(form);
    if (corp) {
      setError(corp);
      setStep(1);
      return;
    }
    const idErr = validateIdentity(form, identityFields);
    if (idErr) {
      setError(idErr);
      setStep(3);
      return;
    }
    const consentErr = validateConsent(form);
    if (consentErr) {
      setError(consentErr);
      setStep(4);
      return;
    }
    if (contactsEnabled) {
      const contactsErr = validateAnchorContacts(form.contacts, contactsCfg.maxUsers);
      if (contactsErr) {
        setError(contactsErr);
        setStep(usersStep);
        return;
      }
    }
    const missing = missingRequiredDocs(byType, docSlots);
    if (missing.length > 0) {
      setError(`Please upload: ${missing.join(', ')}`);
      setStep(2);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await apiClient.put('/api/v1/portal/anchor/onboarding/application', buildPortalSavePayload(form));
      if (resubmit) {
        await apiClient.post('/api/v1/portal/anchor/onboarding/resubmit');
      } else {
        await apiClient.post('/api/v1/portal/anchor/onboarding/submit');
      }
      void navigate('/my-application', { replace: true });
    } catch (e: unknown) {
      setError(apiErrorMessage(e, 'Submit failed'));
    } finally {
      setBusy(false);
    }
  }

  async function onUpload(documentType: string, file: File | null) {
    if (!file || !editable) return;
    setUploadingType(documentType);
    setError(null);
    try {
      const body = new FormData();
      body.append('file', file);
      body.append('documentType', documentType);
      const { data } = await apiClient.post('/api/v1/portal/anchor/onboarding/documents', body);
      const uploaded = unwrapApiData<PortalDocItem>(data);
      setDocs((prev) => {
        const without = prev.filter((d) => d.documentType !== documentType);
        return [...without, uploaded];
      });
    } catch (e: unknown) {
      setError(apiErrorMessage(e, 'Upload failed'));
    } finally {
      setUploadingType(null);
    }
  }

  const cityNames = cities.map((c) => c.cityName).sort((a, b) => a.localeCompare(b));
  const stateInList = filteredStates.some((s) => s.stateName === form.state);
  const cityInList = cityNames.includes(form.city.trim());

  if (loading) {
    return <p className="text-sm text-slate-600">Loading application…</p>;
  }

  return (
    <div className="w-full">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to="/onboarding" className="text-sm font-medium text-slate-700 underline">
            ← Onboarding
          </Link>
          <h1 className="mt-2 text-xl font-semibold text-slate-900">
            {editable ? (resubmit ? 'Update & resubmit application' : 'Complete application') : 'Application details'}
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            {app?.applicationNumber ? (
              <>
                Application <span className="font-medium text-slate-800">{app.applicationNumber}</span>
                {app.status ? ` · ${app.status}` : null}
              </>
            ) : (
              'Fill each section, then review and submit.'
            )}
          </p>
        </div>
        <Link to="/my-application" className={secondaryBtn}>
          My application
        </Link>
      </div>

      {sendBackNotes ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          <p className="font-semibold">Action required — sent back for corrections</p>
          <p className="mt-1 whitespace-pre-wrap">{sendBackNotes}</p>
        </div>
      ) : null}

      {!editable ? (
        <div className="mb-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
          This application is currently under review. Fields are view-only. Track progress on{' '}
          <Link to="/my-application" className="font-medium underline">
            My application
          </Link>
          .
        </div>
      ) : null}

      <Stepper step={step} labels={stepLabels} />
      {error ? <p className="mb-4 text-sm text-red-600">{error}</p> : null}
      {workflowWarning ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          {workflowWarning}
        </div>
      ) : null}
      {geoWarning ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          {geoWarning}
        </div>
      ) : null}

      {step === 0 ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Product &amp; request</h2>
          <p className="text-sm text-slate-600">
            Facility details were set by your lender. These fields are view-only.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <ReadOnlyField label="Onboarding type" value="Anchor (invoice discounting)" />
            <ReadOnlyField label="Entity type" value={form.borrowerType || 'COMPANY'} />
            <ReadOnlyField label="Product" value={loanProductLabel(form.loanProduct)} />
            <ReadOnlyField
              label="Requested amount (INR)"
              value={form.requestedAmount ? Number(form.requestedAmount).toLocaleString('en-IN') : '—'}
            />
            <ReadOnlyField
              label={`Tenure${workflow?.lmsTenureUnit ? ` (${workflow.lmsTenureUnit.toLowerCase()}s)` : ' (months)'}`}
              value={form.tenureMonths || '—'}
            />
            <ReadOnlyField label="Purpose" value={form.purpose || '—'} />
          </div>
        </section>
      ) : null}

      {step === 1 ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Corporate details</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {(
              [
                ['corporateName', 'Corporate name *'],
                ['email', 'Email *'],
                ['mobile', 'Mobile *'],
                ['dateOfIncorporation', 'Date of incorporation *'],
                ['addressLine', 'Address *'],
              ] as const
            ).map(([key, label]) => (
              <div key={key} className={key === 'addressLine' ? 'sm:col-span-2' : undefined}>
                <label className={fieldLabel} htmlFor={key}>
                  {label}
                </label>
                <input
                  id={key}
                  className={fieldInput}
                  type={key === 'dateOfIncorporation' ? 'date' : key === 'email' ? 'email' : 'text'}
                  disabled={!editable}
                  value={form[key]}
                  onChange={(e) => patch({ [key]: e.target.value })}
                />
              </div>
            ))}
            <div>
              <label className={fieldLabel} htmlFor="state">
                State *
              </label>
              <select
                id="state"
                className={fieldInput}
                disabled={!editable || geoStates.length === 0}
                value={form.state}
                onChange={(e) => patch({ state: e.target.value, city: '' })}
              >
                <option value="">{geoStates.length ? 'Select state' : 'States unavailable'}</option>
                {!stateInList && form.state.trim() ? (
                  <option value={form.state}>{form.state} (saved)</option>
                ) : null}
                {filteredStates.map((s) => (
                  <option key={s.id} value={s.stateName}>
                    {s.stateName}
                  </option>
                ))}
              </select>
              {allowedStates && allowedStates.length > 0 ? (
                <p className="mt-1 text-xs text-slate-500">Showing states allowed by your lender’s workflow.</p>
              ) : null}
            </div>
            <div>
              <label className={fieldLabel} htmlFor="city">
                City *
              </label>
              <select
                id="city"
                className={fieldInput}
                disabled={!editable || !form.state.trim() || loadingCities}
                value={form.city}
                onChange={(e) => patch({ city: e.target.value })}
              >
                <option value="">
                  {!form.state.trim()
                    ? 'Select state first'
                    : loadingCities
                      ? 'Loading cities…'
                      : 'Select city'}
                </option>
                {!cityInList && form.city.trim() ? (
                  <option value={form.city}>{form.city} (saved)</option>
                ) : null}
                {cityNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={fieldLabel} htmlFor="pincode">
                Pincode *
              </label>
              <input
                id="pincode"
                className={fieldInput}
                disabled={!editable}
                inputMode="numeric"
                maxLength={6}
                value={form.pincode}
                onChange={(e) => patch({ pincode: e.target.value.replace(/\D/g, '').slice(0, 6) })}
              />
            </div>
            <div>
              <label className={fieldLabel} htmlFor="country">
                Country
              </label>
              <input
                id="country"
                className={fieldInput}
                disabled={!editable}
                value={form.country}
                onChange={(e) => patch({ country: e.target.value })}
              />
            </div>
          </div>
        </section>
      ) : null}

      {step === 2 ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Documents</h2>
          <p className="text-sm text-slate-600">
            Upload the required entity documents
            {workflow?.name ? ` for workflow “${workflow.name}”` : ' for KYC and credit review'}.
          </p>
          <ul className="space-y-3">
            {docSlots.map((slot) => {
              const existing = byType[slot.documentType];
              const required = slot.required !== false && !slot.optional;
              return (
                <li
                  key={slot.documentType}
                  className="flex flex-col gap-2 rounded-lg border border-slate-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <p className="text-sm font-medium text-slate-900">
                      {slot.label}
                      {required ? <span className="ml-1 text-red-500">*</span> : (
                        <span className="ml-2 text-xs font-normal text-slate-500">(optional)</span>
                      )}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">{slot.reason}</p>
                    <p className="mt-1 text-xs font-medium text-slate-700">
                      {existing ? (
                        <span className="text-emerald-700">Received: {existing.fileName ?? 'file uploaded'}</span>
                      ) : (
                        <span className="text-amber-700">Not uploaded</span>
                      )}
                    </p>
                  </div>
                  {editable ? (
                    <label className={`${secondaryBtn} cursor-pointer`}>
                      {uploadingType === slot.documentType ? 'Uploading…' : existing ? 'Replace' : 'Upload'}
                      <input
                        type="file"
                        className="hidden"
                        disabled={uploadingType != null}
                        onChange={(e) => {
                          const f = e.target.files?.[0] ?? null;
                          e.target.value = '';
                          void onUpload(slot.documentType, f);
                        }}
                      />
                    </label>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}

      {step === 3 ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Identity / KYC</h2>
          <p className="text-sm text-slate-600">
            Fields follow the active anchor workflow KYC / identity configuration
            {workflow?.intakeIdentitySchema?.length || (workflow?.steps?.length ?? 0) > 0 ? '' : ' defaults'}.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            {identityFields.map((fld) => (
              <div key={fld.key}>
                <label className={fieldLabel} htmlFor={fld.key}>
                  {fld.label}
                  {fld.required ? ' *' : ''}
                </label>
                <input
                  id={fld.key}
                  className={fieldInput}
                  disabled={!editable}
                  maxLength={fld.maxLength}
                  value={form[fld.key]}
                  onChange={(e) =>
                    patch({
                      [fld.key]:
                        fld.key === 'entityPan' || fld.key === 'gstin' || fld.key === 'cin' || fld.key === 'ifscCode'
                          ? e.target.value.toUpperCase()
                          : e.target.value,
                    })
                  }
                />
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {step === 4 ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Consent</h2>
          <p className="text-sm text-slate-600">Please confirm the following to proceed with verification.</p>
          <div className="space-y-3">
            {(
              [
                ['consentKyc', 'I consent to KYC verification of the entity and authorised persons.'],
                ['consentBureau', 'I consent to credit bureau checks for this onboarding.'],
                ['consentAccountAggregator', 'I consent to account aggregator / financial data verification where applicable.'],
                ['consentComms', 'I consent to receive communications about this application by email/SMS.'],
              ] as const
            ).map(([key, label]) => (
              <label key={key} className="flex items-start gap-3 text-sm text-slate-800">
                <input
                  type="checkbox"
                  className="mt-1"
                  disabled={!editable}
                  checked={form[key]}
                  onChange={(e) => patch({ [key]: e.target.checked })}
                />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </section>
      ) : null}

      {contactsEnabled && step === usersStep ? (
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <AnchorContactsUsersSection
            contacts={form.contacts}
            maxUsers={contactsCfg.maxUsers}
            disabled={!editable}
            onChange={(contacts) => setForm((f) => ({ ...f, contacts }))}
          />
        </div>
      ) : null}

      {step === reviewStep ? (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-base font-semibold text-slate-900">Review &amp; submit</h2>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Product</dt>
              <dd className="font-medium text-slate-900">{loanProductLabel(form.loanProduct)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Requested amount</dt>
              <dd className="font-medium text-slate-900">
                {form.requestedAmount ? `₹${Number(form.requestedAmount).toLocaleString('en-IN')}` : '—'}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Corporate name</dt>
              <dd className="font-medium text-slate-900">{form.corporateName || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Location</dt>
              <dd className="font-medium text-slate-900">
                {[form.city, form.state, form.pincode].filter(Boolean).join(', ') || '—'}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Identity</dt>
              <dd className="font-medium text-slate-900">
                {identityFields.map((f) => form[f.key] || '—').join(' · ')}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Documents uploaded</dt>
              <dd className="font-medium text-slate-900">
                {docSlots.filter((s) => byType[s.documentType]).length} / {docSlots.length}
              </dd>
            </div>
            {contactsEnabled && form.contacts.length > 0 ? (
              <div className="sm:col-span-2">
                <dt className="text-slate-500">Users</dt>
                <dd className="font-medium text-slate-900">
                  <ul className="mt-1 space-y-1">
                    {form.contacts.map((c) => (
                      <li key={c.id}>
                        {c.name} · {c.email} · {c.role.replace('_', ' ')}
                        {c.isSigningAuthority
                          ? ` · signing${form.contacts.filter((x) => x.isSigningAuthority).length > 1 ? ` #${c.signingOrder}` : ''}`
                          : ''}
                      </li>
                    ))}
                  </ul>
                </dd>
              </div>
            ) : null}
            <div className="sm:col-span-2">
              <dt className="text-slate-500">Consents</dt>
              <dd className="font-medium text-slate-900">
                KYC {form.consentKyc ? '✓' : '✗'} · Bureau {form.consentBureau ? '✓' : '✗'} · AA{' '}
                {form.consentAccountAggregator ? '✓' : '✗'} · Comms {form.consentComms ? '✓' : '✗'}
              </dd>
            </div>
          </dl>
          {editable ? (
            <p className="text-sm text-slate-600">
              Submitting will send this application to your lender for review
              {resubmit ? ' (resubmission after send-back)' : ''}.
            </p>
          ) : null}
        </section>
      ) : null}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          className={secondaryBtn}
          disabled={busy || step === 0}
          onClick={() => {
            setError(null);
            setStep((s) => Math.max(0, s - 1));
          }}
        >
          Back
        </button>
        <div className="flex flex-wrap gap-3">
          {editable && step > 0 && step < reviewStep ? (
            <button
              type="button"
              className={secondaryBtn}
              disabled={busy}
              onClick={() => {
                void saveSection();
              }}
            >
              {busy ? 'Saving…' : 'Save draft'}
            </button>
          ) : null}
          {step < reviewStep ? (
            <button type="button" className={primaryBtn} disabled={busy || uploadingType != null} onClick={() => void onNext()}>
              {busy ? 'Please wait…' : 'Continue'}
            </button>
          ) : editable ? (
            <button type="button" className={primaryBtn} disabled={busy} onClick={() => void onSubmit()}>
              {busy ? 'Submitting…' : resubmit ? 'Resubmit application' : 'Submit application'}
            </button>
          ) : (
            <Link to="/my-application" className={primaryBtn}>
              View status
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
