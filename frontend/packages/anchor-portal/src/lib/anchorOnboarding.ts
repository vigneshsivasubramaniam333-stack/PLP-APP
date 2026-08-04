import type { AnchorOnboardingApplication } from '@plp/shared';
import type { AnchorContactUser } from './anchorContacts';
import {
  contactsFromBusinessInfo,
  contactsToBusinessInfoPayload,
  primaryContactFromCorporate,
} from './anchorContacts';

export const ANCHOR_ONBOARDING_BASE_STEPS = [
  'Product & request',
  'Corporate details',
  'Documents',
  'Identity / KYC',
  'Consent',
] as const;

/** @deprecated Use resolveOnboardingStepLabels — kept for callers that ignore contacts config. */
export const ANCHOR_ONBOARDING_STEPS = [
  ...ANCHOR_ONBOARDING_BASE_STEPS,
  'Review & submit',
] as const;

export type AnchorOnboardingStepIndex = 0 | 1 | 2 | 3 | 4 | 5 | 6;

export function resolveOnboardingStepLabels(contactsEnabled: boolean): readonly string[] {
  if (contactsEnabled) {
    return [...ANCHOR_ONBOARDING_BASE_STEPS, 'Users', 'Review & submit'];
  }
  return [...ANCHOR_ONBOARDING_BASE_STEPS, 'Review & submit'];
}

export type AnchorDocSlot = {
  documentType: string;
  label: string;
  reason: string;
  optional?: boolean;
  required?: boolean;
};

/** Mirrors LOS documentSlotsForAnchorIntake for COMPANY (excludes Aadhaar/photo). */
export const ANCHOR_DOCUMENT_SLOTS: AnchorDocSlot[] = [
  {
    documentType: 'PAN_CARD',
    label: 'PAN card',
    reason: 'Used to verify identity and match the entity name with the income tax record.',
    required: true,
  },
  {
    documentType: 'BANK_STATEMENT',
    label: 'Bank statement',
    reason: 'Shows cash flows so we can assess ability to repay and validate activity.',
    required: true,
  },
  {
    documentType: 'GST_RETURN',
    label: 'GST return / GSTR',
    reason: 'Helps confirm turnover and business continuity for the entity.',
    required: true,
  },
  {
    documentType: 'BUSINESS_PROOF',
    label: 'Business proof (Udyam, license, or board resolution)',
    reason: 'Proves the business exists and its structure.',
    required: true,
  },
  {
    documentType: 'OTHER',
    label: 'Other supporting documents',
    reason: 'Optional extras the credit team may need.',
    optional: true,
    required: false,
  },
];

const ANCHOR_EXCLUDED_DOCS = new Set(['AADHAAR', 'PHOTOGRAPH']);

export type PortalIdentityField = {
  key: keyof Pick<
    AnchorPortalFormState,
    'entityPan' | 'gstin' | 'cin' | 'bankAccountNumber' | 'ifscCode' | 'accountHolderName'
  >;
  label: string;
  required: boolean;
  maxLength?: number;
};

const IDENTITY_KEYS = new Set([
  'entityPan',
  'gstin',
  'cin',
  'bankAccountNumber',
  'ifscCode',
  'accountHolderName',
]);

export type PortalWorkflowSnapshot = {
  id?: string;
  name?: string;
  borrowerType?: string;
  loanProduct?: string;
  lmsTenureUnit?: string | null;
  intakeIdentitySchema?: Record<string, unknown>[] | null;
  intakeConfig?: {
    policy?: string;
    tenureRules?: unknown;
    locationRules?: { allowedStates?: string[] };
    standaloneDocuments?: { documentType: string; required?: boolean; label?: string }[];
    contacts?: { enabled?: boolean; maxUsers?: number };
  } | null;
  steps?: Record<string, unknown>[];
};

export type AnchorPortalFormState = {
  loanProduct: string;
  borrowerType: string;
  requestedAmount: string;
  tenureMonths: string;
  purpose: string;
  corporateName: string;
  email: string;
  mobile: string;
  dateOfIncorporation: string;
  addressLine: string;
  city: string;
  state: string;
  country: string;
  pincode: string;
  entityPan: string;
  gstin: string;
  cin: string;
  bankAccountNumber: string;
  ifscCode: string;
  accountHolderName: string;
  consentKyc: boolean;
  consentBureau: boolean;
  consentAccountAggregator: boolean;
  consentComms: boolean;
  contacts: AnchorContactUser[];
};

export function emptyAnchorPortalForm(): AnchorPortalFormState {
  return {
    loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
    borrowerType: 'COMPANY',
    requestedAmount: '',
    tenureMonths: '',
    purpose: '',
    corporateName: '',
    email: '',
    mobile: '',
    dateOfIncorporation: '',
    addressLine: '',
    city: '',
    state: '',
    country: 'India',
    pincode: '',
    entityPan: '',
    gstin: '',
    cin: '',
    bankAccountNumber: '',
    ifscCode: '',
    accountHolderName: '',
    consentKyc: false,
    consentBureau: false,
    consentAccountAggregator: false,
    consentComms: false,
    contacts: [],
  };
}

function str(v: unknown): string {
  return v == null ? '' : String(v);
}

function truthyFlag(v: unknown): boolean {
  if (typeof v === 'boolean') return v;
  if (v == null) return false;
  const s = String(v).trim().toLowerCase();
  return s === 'true' || s === '1' || s === 'yes';
}

export function hydrateFormFromApplication(app: AnchorOnboardingApplication | null | undefined): AnchorPortalFormState {
  const base = emptyAnchorPortalForm();
  if (!app) return base;
  const bi = (app.businessInfo ?? {}) as Record<string, unknown>;
  const pi = (app.personalInfo ?? {}) as Record<string, unknown>;
  const fi = (app.financialInfo ?? {}) as Record<string, unknown>;
  const email = str(bi.email);
  const mobile = str(bi.mobile || pi.mobile || pi.phone);
  const corporateName = str(bi.corporateName);
  let contacts = contactsFromBusinessInfo(bi.contacts);
  if (contacts.length === 0 && (email || mobile || corporateName)) {
    contacts = [
      primaryContactFromCorporate({
        name: str(bi.accountHolderName) || corporateName,
        email,
        mobile,
      }),
    ];
  }
  return {
    ...base,
    loanProduct: str(app.loanProduct) || base.loanProduct,
    borrowerType: str(app.borrowerType) || base.borrowerType,
    requestedAmount: app.requestedAmount != null ? String(app.requestedAmount) : '',
    tenureMonths: app.tenureMonths != null ? String(app.tenureMonths) : '',
    purpose: str(pi.purpose),
    corporateName,
    email,
    mobile,
    dateOfIncorporation: str(bi.dateOfIncorporation),
    addressLine: str(bi.addressLine),
    city: str(bi.city),
    state: str(bi.state),
    country: str(bi.country) || 'India',
    pincode: str(bi.pincode),
    entityPan: str(bi.entityPan || bi.pan).toUpperCase(),
    gstin: str(bi.gstin).toUpperCase(),
    cin: str(bi.cin).toUpperCase(),
    bankAccountNumber: str(bi.bankAccountNumber),
    ifscCode: str(bi.ifscCode).toUpperCase(),
    accountHolderName: str(bi.accountHolderName),
    consentKyc: truthyFlag(fi.consentKyc ?? pi.consentKyc),
    consentBureau: truthyFlag(fi.consentBureau ?? pi.consentBureau),
    consentAccountAggregator: truthyFlag(fi.consentAccountAggregator ?? pi.consentAccountAggregator),
    consentComms: truthyFlag(fi.consentComms ?? pi.consentComms),
    contacts,
  };
}

/** Corporate + identity + consent PUT body aligned with LOS staff AnchorIntakeWizard. */
export function buildPortalSavePayload(form: AnchorPortalFormState): Record<string, unknown> {
  const contacts =
    form.contacts.length > 0 ? contactsToBusinessInfoPayload(form.contacts) : undefined;
  return {
    personalInfo: {
      phone: form.mobile.trim(),
      mobile: form.mobile.trim(),
      ...(form.purpose.trim() ? { purpose: form.purpose.trim() } : {}),
    },
    businessInfo: {
      corporateName: form.corporateName.trim(),
      email: form.email.trim(),
      mobile: form.mobile.trim(),
      dateOfIncorporation: form.dateOfIncorporation.trim(),
      addressLine: form.addressLine.trim(),
      city: form.city.trim(),
      state: form.state.trim(),
      country: form.country.trim() || 'India',
      pincode: form.pincode.replace(/\D/g, '').slice(0, 6),
      entityPan: form.entityPan.trim().toUpperCase(),
      gstin: form.gstin.trim().toUpperCase(),
      cin: form.cin.trim().toUpperCase(),
      bankAccountNumber: form.bankAccountNumber.trim(),
      ifscCode: form.ifscCode.trim().toUpperCase(),
      accountHolderName: form.accountHolderName.trim(),
      ...(contacts ? { contacts } : {}),
    },
    financialInfo: {
      consentKyc: form.consentKyc ? 'true' : 'false',
      consentBureau: form.consentBureau ? 'true' : 'false',
      consentAccountAggregator: form.consentAccountAggregator ? 'true' : 'false',
      consentComms: form.consentComms ? 'true' : 'false',
      consentRecordedByName: 'ANCHOR_PORTAL',
    },
  };
}

export function unwrapApiData<T>(body: unknown): T {
  if (body && typeof body === 'object' && 'data' in body) {
    const data = (body as { data?: T }).data;
    if (data !== undefined && data !== null) return data;
  }
  return body as T;
}

export function loanProductLabel(code: string): string {
  if (!code) return '—';
  if (code.includes('INVOICE')) return 'Invoice discounting';
  return code.replace(/_/g, ' ');
}

/** Portal edit surface for the current LOS application status. */
export type PortalEditMode = 'NONE' | 'FULL_INTAKE' | 'DOCUMENTS_ONLY';

/**
 * FULL_INTAKE — first fill / intake send-back (all sections).
 * DOCUMENTS_ONLY — post-eSign document verification send-back (upload + resubmit only).
 * NONE — under review / complete (view-only).
 */
export function resolvePortalEditMode(losStatus: string | null | undefined): PortalEditMode {
  if (!losStatus) return 'FULL_INTAKE';
  const s = losStatus.toUpperCase();
  if (s === 'DOC_VERIFICATION_SENT_BACK') return 'DOCUMENTS_ONLY';
  if (s === 'ANCHOR_CONSENT_PENDING' || s === 'ANCHOR_SENT_BACK' || s === 'DRAFT') return 'FULL_INTAKE';
  return 'NONE';
}

/** True when the anchor may change any part of the application (full intake or documents-only). */
export function isEditableIntakeStatus(losStatus: string | null | undefined): boolean {
  return resolvePortalEditMode(losStatus) !== 'NONE';
}

export function isDocumentsOnlyMode(losStatus: string | null | undefined): boolean {
  return resolvePortalEditMode(losStatus) === 'DOCUMENTS_ONLY';
}

export function needsResubmit(losStatus: string | null | undefined): boolean {
  if (!losStatus) return false;
  const s = losStatus.toUpperCase();
  return s === 'ANCHOR_SENT_BACK' || s === 'DOC_VERIFICATION_SENT_BACK';
}

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i;
const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/i;

export function validateCorporate(form: AnchorPortalFormState): string | null {
  if (!form.corporateName.trim()) return 'Corporate name is required';
  if (!form.email.trim()) return 'Email is required';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return 'Enter a valid email';
  if (!form.mobile.trim() || form.mobile.replace(/\D/g, '').length < 10) return 'Enter a valid 10-digit mobile';
  if (!form.dateOfIncorporation.trim()) return 'Date of incorporation is required';
  if (!form.addressLine.trim()) return 'Address is required';
  if (!form.state.trim()) return 'State is required';
  if (!form.city.trim()) return 'City is required';
  if (form.pincode.replace(/\D/g, '').length !== 6) return 'Enter a valid 6-digit pincode';
  return null;
}

export function defaultIdentityFields(borrowerType: string): PortalIdentityField[] {
  const base: PortalIdentityField[] = [
    { key: 'entityPan', label: 'Entity PAN', required: true, maxLength: 10 },
    { key: 'gstin', label: 'GSTIN', required: false, maxLength: 15 },
  ];
  if (borrowerType === 'COMPANY') {
    base.push({ key: 'cin', label: 'CIN (Corporate Identification Number)', required: true, maxLength: 21 });
  } else {
    base.push({ key: 'cin', label: 'CIN (if applicable)', required: false, maxLength: 21 });
  }
  base.push(
    { key: 'bankAccountNumber', label: 'Bank account number', required: true },
    { key: 'ifscCode', label: 'IFSC', required: true, maxLength: 11 },
    { key: 'accountHolderName', label: 'Account holder name', required: true },
  );
  return base;
}

function fieldRequiredAtIntake(step: Record<string, unknown>): boolean {
  if (step.fieldRequiredAtIntake != null) return step.fieldRequiredAtIntake === true;
  return step.mandatory !== false;
}

function collectAtIntake(step: Record<string, unknown>): boolean {
  if (step.collectAtIntake != null) return step.collectAtIntake === true;
  return true;
}

function identityFieldsFromKycSteps(
  workflow: PortalWorkflowSnapshot | null | undefined,
): PortalIdentityField[] | null {
  const steps = workflow?.steps ?? [];
  if (steps.length === 0) return null;
  const identityRelated = new Set(['PAN_VERIFY', 'GSTIN_VERIFY', 'CIN_MCA21', 'BANK_PENNY_DROP']);
  const hasIdentity = steps.some((s) => identityRelated.has(String(s.step ?? '').trim().toUpperCase()));
  if (!hasIdentity) return null;

  const out: PortalIdentityField[] = [];
  const seen = new Set<string>();
  for (const step of steps) {
    if (!collectAtIntake(step)) continue;
    const stepName = String(step.step ?? '').trim().toUpperCase();
    if (!identityRelated.has(stepName)) continue;
    const required = fieldRequiredAtIntake(step);
    if (stepName === 'PAN_VERIFY' && !seen.has('entityPan')) {
      seen.add('entityPan');
      out.push({ key: 'entityPan', label: 'Entity PAN', required, maxLength: 10 });
    }
    if (stepName === 'GSTIN_VERIFY' && !seen.has('gstin')) {
      seen.add('gstin');
      out.push({ key: 'gstin', label: 'GSTIN', required, maxLength: 15 });
    }
    if (stepName === 'CIN_MCA21' && !seen.has('cin')) {
      seen.add('cin');
      out.push({ key: 'cin', label: 'CIN (Corporate Identification Number)', required, maxLength: 21 });
    }
    if (stepName === 'BANK_PENNY_DROP') {
      if (!seen.has('bankAccountNumber')) {
        seen.add('bankAccountNumber');
        out.push({ key: 'bankAccountNumber', label: 'Bank account number', required });
      }
      if (!seen.has('ifscCode')) {
        seen.add('ifscCode');
        out.push({ key: 'ifscCode', label: 'IFSC', required, maxLength: 11 });
      }
      if (!seen.has('accountHolderName')) {
        seen.add('accountHolderName');
        out.push({ key: 'accountHolderName', label: 'Account holder name', required });
      }
    }
  }
  return out.length > 0 ? out : null;
}

export function parsePortalIdentitySchema(
  workflow: PortalWorkflowSnapshot | null | undefined,
  borrowerType: string,
): PortalIdentityField[] {
  const raw = workflow?.intakeIdentitySchema;
  if (Array.isArray(raw) && raw.length > 0) {
    const out: PortalIdentityField[] = [];
    for (const row of raw) {
      if (!row || typeof row !== 'object') continue;
      const m = row as Record<string, unknown>;
      const key = String(m.key ?? '');
      if (!IDENTITY_KEYS.has(key)) continue;
      out.push({
        key: key as PortalIdentityField['key'],
        label: String(m.label ?? key),
        required: Boolean(m.required),
        maxLength: typeof m.maxLength === 'number' ? m.maxLength : undefined,
      });
    }
    if (out.length) return out;
  }
  const fromSteps = identityFieldsFromKycSteps(workflow);
  if (fromSteps?.length) return fromSteps;
  return defaultIdentityFields(borrowerType);
}

export function validateIdentity(
  form: AnchorPortalFormState,
  fields?: PortalIdentityField[],
): string | null {
  const identityFields = fields ?? defaultIdentityFields(form.borrowerType);
  for (const f of identityFields) {
    const v = String(form[f.key] ?? '').trim();
    if (f.required && !v) return `${f.label} is required`;
    if (f.key === 'entityPan' && v && !PAN_RE.test(v)) return 'Enter a valid entity PAN (e.g. ABCDE1234F)';
    if (f.key === 'gstin' && v && v.length !== 15) return 'GSTIN must be 15 characters when provided';
    if (f.key === 'ifscCode' && v && !IFSC_RE.test(v)) return 'Enter a valid IFSC';
    if (f.maxLength && v.length > f.maxLength) return `${f.label} must be at most ${f.maxLength} characters`;
  }
  return null;
}

export function validateConsent(form: AnchorPortalFormState): string | null {
  if (!form.consentKyc || !form.consentBureau || !form.consentAccountAggregator || !form.consentComms) {
    return 'All consents are required before you can continue';
  }
  return null;
}

export type PortalDocItem = {
  id?: string;
  documentType?: string;
  fileName?: string;
  createdAt?: string;
};

export function docsByType(docs: PortalDocItem[]): Record<string, PortalDocItem> {
  const map: Record<string, PortalDocItem> = {};
  for (const d of docs) {
    const t = d.documentType;
    if (!t) continue;
    map[t] = d;
  }
  return map;
}

function isWorkflowDriven(workflow: PortalWorkflowSnapshot | null | undefined): boolean {
  return String(workflow?.intakeConfig?.policy ?? '').toUpperCase() === 'WORKFLOW_DRIVEN';
}

export function resolveAllowedStatesFromWorkflow(
  workflow: PortalWorkflowSnapshot | null | undefined,
): string[] | null {
  if (!isWorkflowDriven(workflow)) return null;
  const raw = workflow?.intakeConfig?.locationRules?.allowedStates;
  if (!Array.isArray(raw) || raw.length === 0) return null;
  const names = raw.map((s) => String(s ?? '').trim()).filter(Boolean);
  return names.length > 0 ? names : null;
}

const DOC_LABELS: Record<string, string> = {
  PAN_CARD: 'PAN card',
  BANK_STATEMENT: 'Bank statement',
  GST_RETURN: 'GST return / GSTR',
  BUSINESS_PROOF: 'Business proof',
  OTHER: 'Other supporting documents',
  PHOTOGRAPH: 'Photograph',
  AADHAAR: 'Aadhaar',
  VOTER_ID: 'Voter ID',
  DRIVING_LICENSE: 'Driving licence',
};

/** Mirrors LOS ui-service kycStepIntakeCatalog defaults for document inference. */
const KYC_STEP_DEFAULT_DOCS: Record<string, { docs: string[]; label: string }> = {
  PAN_VERIFY: { docs: ['PAN_CARD'], label: 'PAN' },
  AADHAAR_OTP: { docs: ['AADHAAR'], label: 'Aadhaar' },
  VOTER_ID_VERIFY: { docs: ['VOTER_ID'], label: 'Voter ID' },
  DL_VERIFY: { docs: ['DRIVING_LICENSE'], label: 'Driving licence' },
  GSTIN_VERIFY: { docs: ['GST_RETURN'], label: 'GSTIN' },
  BANK_PENNY_DROP: { docs: ['BANK_STATEMENT'], label: 'Bank account' },
  FACE_MATCH: { docs: ['PHOTOGRAPH'], label: 'Face match' },
  LIVENESS: { docs: ['PHOTOGRAPH'], label: 'Liveness' },
};

function portalCollectAtIntake(step: Record<string, unknown>): boolean {
  if (step.collectAtIntake != null) return step.collectAtIntake === true;
  const stepName = String(step.step ?? '').trim().toUpperCase();
  return Boolean(KYC_STEP_DEFAULT_DOCS[stepName]);
}

function labelForDoc(documentType: string, fallback?: string): string {
  const key = documentType.toUpperCase();
  return DOC_LABELS[key] ?? fallback ?? documentType.replace(/_/g, ' ');
}

/** Resolve document slots from workflow KYC steps + standalone docs; fall back to defaults. */
export function resolvePortalDocumentSlots(
  workflow: PortalWorkflowSnapshot | null | undefined,
): AnchorDocSlot[] {
  const byType = new Map<string, AnchorDocSlot>();
  const add = (documentType: string, label: string, required: boolean) => {
    const key = documentType.toUpperCase();
    if (!key || ANCHOR_EXCLUDED_DOCS.has(key)) return;
    const existing = byType.get(key);
    if (existing) {
      if (required) {
        existing.required = true;
        existing.optional = false;
      }
      return;
    }
    byType.set(key, {
      documentType: key,
      label: labelForDoc(key, label),
      reason: required ? 'Required for this workflow.' : 'Optional for this workflow.',
      required,
      optional: !required,
    });
  };

  if (isWorkflowDriven(workflow)) {
    for (const step of workflow?.steps ?? []) {
      if (!portalCollectAtIntake(step)) continue;
      const stepName = String(step.step ?? '').trim().toUpperCase();
      const meta = KYC_STEP_DEFAULT_DOCS[stepName];
      const docs = step.documentsRequired as { documentType?: string; required?: boolean }[] | undefined;
      if (Array.isArray(docs) && docs.length > 0) {
        for (const d of docs) {
          if (d.documentType) {
            add(d.documentType, d.documentType.replace(/_/g, ' '), d.required !== false);
          }
        }
      } else if (step.documentRequired === true && meta) {
        for (const dt of meta.docs) {
          add(dt, labelForDoc(dt, meta.label), true);
        }
      } else if (meta) {
        for (const dt of meta.docs) {
          add(dt, labelForDoc(dt, meta.label), false);
        }
      }
    }
  } else {
    for (const slot of ANCHOR_DOCUMENT_SLOTS) {
      add(slot.documentType, slot.label, slot.required !== false && !slot.optional);
    }
  }

  // Standalone docs always apply when configured on the workflow.
  for (const doc of workflow?.intakeConfig?.standaloneDocuments ?? []) {
    if (doc.documentType) {
      add(doc.documentType, doc.label ?? doc.documentType.replace(/_/g, ' '), doc.required === true);
    }
  }

  // eSign additional documents marked collectAtIntake (default when required) must appear as slots.
  for (const step of workflow?.steps ?? []) {
    const stepName = String(step.step ?? '').trim().toUpperCase();
    if (stepName !== 'ESIGN_AGREEMENT' && stepName !== 'ESIGN' && stepName !== 'ESIGN_KFS') continue;
    const esignDocs = step.esignDocuments as
      | {
          additional?: {
            documentType?: string;
            label?: string;
            required?: boolean;
            collectAtIntake?: boolean;
          }[];
        }
      | undefined;
    if (!esignDocs?.additional?.length) continue;
    for (const d of esignDocs.additional) {
      if (!d?.documentType) continue;
      const required = d.required !== false;
      const collect =
        d.collectAtIntake != null ? d.collectAtIntake === true : required;
      if (!collect) continue;
      add(d.documentType, d.label ?? d.documentType.replace(/_/g, ' '), required);
    }
  }

  const slots = [...byType.values()];
  return slots.length > 0 ? slots : ANCHOR_DOCUMENT_SLOTS;
}

export function missingRequiredDocs(
  byType: Record<string, PortalDocItem>,
  slots: AnchorDocSlot[] = ANCHOR_DOCUMENT_SLOTS,
): string[] {
  return slots
    .filter((s) => (s.required !== false && !s.optional) && !byType[s.documentType])
    .map((s) => s.label);
}

export type GeoStateOption = { id: string; stateName: string; stateCode?: string };
export type GeoCityOption = { id: string; cityName: string; stateId?: string };
