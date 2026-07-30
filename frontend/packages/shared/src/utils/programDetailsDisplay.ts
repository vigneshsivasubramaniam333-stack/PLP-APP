import type { Program, ProgramEligibilityConfig, ProgramOperationalParameters, SubProgram, SubProgramBorrower } from '../types';

export type ProgramDetailRow = { label: string; value: string };

function parseBool(raw: unknown): boolean {
  return raw === true || raw === 'true' || raw === 'YES';
}

function yesNo(raw: unknown): string {
  return parseBool(raw) ? 'Yes' : 'No';
}

function fmtNum(raw: unknown, suffix = ''): string | null {
  if (raw == null || raw === '') return null;
  const n = Number(raw);
  if (Number.isNaN(n)) return null;
  return `${n}${suffix}`;
}

function fmtPct(raw: unknown): string | null {
  const s = fmtNum(raw, '%');
  return s;
}

function pushRow(rows: ProgramDetailRow[], label: string, value: string | null | undefined) {
  if (value == null || value === '') return;
  rows.push({ label, value });
}

function parseParams(raw: Program['parameters']): ProgramOperationalParameters {
  if (!raw || typeof raw !== 'object') return {};
  return raw as ProgramOperationalParameters;
}

function parseCfg(raw: Program['config']): ProgramEligibilityConfig {
  if (!raw || typeof raw !== 'object') return {};
  return raw as ProgramEligibilityConfig;
}

/** Read-only rows for program-level eligibility + operational parameters. */
export function buildProgramConfigurationRows(program: Program | null | undefined): ProgramDetailRow[] {
  if (!program) return [];
  const rows: ProgramDetailRow[] = [];
  const cfg = parseCfg(program.config);
  const params = parseParams(program.parameters);
  const isId = program.productType === 'INVOICE_DISCOUNTING';

  if (isId) {
    pushRow(rows, 'Max invoice vintage (days)', fmtNum(cfg.maxInvoiceAgeDays));
    pushRow(rows, 'Interest payment', cfg.interestPayment ? String(cfg.interestPayment).replace(/_/g, ' ') : null);
    pushRow(rows, 'Max CMR', fmtNum(cfg.maxCmr));
    pushRow(rows, 'Min CIBIL', fmtNum(cfg.minCibil));
    pushRow(rows, 'Min invoice amount', cfg.minInvoiceAmount != null ? `₹${Number(cfg.minInvoiceAmount).toLocaleString('en-IN')}` : null);
    pushRow(rows, 'Min days to due date', fmtNum(cfg.minDaysToDueDate));
    pushRow(rows, 'Dependency vintage (%)', fmtNum(cfg.dependencyVintagePercent));
    pushRow(rows, 'Min Dir Relationship (months)', fmtNum(cfg.anchorRelationshipVintageMonths));
  }

  pushRow(rows, 'Enable payment for borrower', yesNo(params.enablePaymentForBorrower));
  pushRow(rows, 'Auto payment (borrower)', yesNo(params.autoPaymentBorrower));
  pushRow(rows, 'Auto discounting', yesNo(params.autoDiscounting));
  pushRow(rows, 'Discounting day (of month)', fmtNum(params.discountingDay));
  pushRow(rows, 'Gap b/w discounting (days)', fmtNum(params.gapBetweenDiscountingDays));
  pushRow(rows, 'Gap b/w previous invoice (days)', fmtNum(params.gapBetweenPreviousInvoiceDays));
  pushRow(rows, 'Auto pull option', yesNo(params.autoPullOption));
  pushRow(rows, 'Auto accept invoices', yesNo(params.autoAcceptInvoices));
  pushRow(rows, 'Sanction type', params.sanctionType ? String(params.sanctionType).toUpperCase() : null);
  pushRow(rows, 'Gap b/w sanction & disbursement (days)', fmtNum(params.gapBetweenSanctionAndDisbursementDays));
  pushRow(rows, 'Partial discount', yesNo(params.partialDiscount));
  pushRow(rows, 'Invoice delete allowed', yesNo(params.invoiceDelete));
  pushRow(rows, 'Interest-free credit period', yesNo(params.intFreeCreditPeriod));
  pushRow(rows, 'Interest-free period (days)', fmtNum(params.intFreePeriodDays));
  pushRow(rows, 'LMS entry', program.lmsEntryIn === 'YES' ? 'Yes' : program.lmsEntryIn === 'NO' ? 'No' : null);
  if (program.lmsEntryIn === 'YES') {
    pushRow(rows, 'LMS product code', program.encoreProductCode?.trim() || null);
  }

  return rows;
}

/** Sub-program pricing and flow defaults (read-only). */
export function buildSubProgramConfigurationRows(subProgram: SubProgram | null | undefined): ProgramDetailRow[] {
  if (!subProgram) return [];
  const rows: ProgramDetailRow[] = [];
  pushRow(rows, 'Flow type', subProgram.flowType?.replace(/_/g, ' ') ?? null);
  pushRow(rows, 'Anchor role', subProgram.anchorRole ?? null);
  pushRow(rows, 'Borrower role', subProgram.borrowerRole ?? null);
  pushRow(rows, 'Sub-program limit', subProgram.subProgramLimit != null ? `₹${Number(subProgram.subProgramLimit).toLocaleString('en-IN')}` : null);
  pushRow(rows, 'Interest rate', fmtPct(subProgram.interestRate));
  pushRow(rows, 'Discount margin', fmtPct(subProgram.marginPercent));
  pushRow(rows, 'Max tenure (days)', fmtNum(subProgram.maxTenureDays));
  return rows;
}

export type BorrowerTermsLike = Pick<
  SubProgramBorrower,
  'interestRate' | 'discountMarginPercent' | 'creditPeriodDays' | 'discountHold' | 'paymentMethod' | 'overdueInterestRate'
>;

/** Per-borrower overrides on a sub-program (read-only). */
export function buildBorrowerTermsRows(terms: BorrowerTermsLike | null | undefined): ProgramDetailRow[] {
  if (!terms) return [];
  const rows: ProgramDetailRow[] = [];
  pushRow(rows, 'Interest rate', terms.interestRate != null ? `${terms.interestRate}%` : null);
  pushRow(rows, 'Discount margin', terms.discountMarginPercent != null ? `${terms.discountMarginPercent}%` : null);
  pushRow(rows, 'Credit period (days)', fmtNum(terms.creditPeriodDays));
  pushRow(rows, 'Discount hold', terms.discountHold === 'YES' ? 'Yes' : terms.discountHold === 'NO' ? 'No' : null);
  pushRow(rows, 'Payment method', terms.paymentMethod?.replace(/_/g, ' ') ?? null);
  pushRow(rows, 'Overdue interest rate', terms.overdueInterestRate != null ? `${terms.overdueInterestRate}%` : null);
  return rows;
}

/** Build rows from plain maps (LOS API payloads). */
export function buildProgramConfigurationRowsFromMaps(
  productType: string | null | undefined,
  config: Record<string, unknown> | null | undefined,
  parameters: Record<string, unknown> | null | undefined,
  lmsEntryIn?: string | null,
  encoreProductCode?: string | null,
): ProgramDetailRow[] {
  return buildProgramConfigurationRows({
    id: '',
    programCode: '',
    programName: '',
    productType: (productType as Program['productType']) ?? 'INVOICE_DISCOUNTING',
    lenderId: '',
    programLimit: 0,
    maxBorrowerLimit: 0,
    defaultInterestRate: 0,
    maxTenureDays: 0,
    status: 'ACTIVE',
    config: config ?? undefined,
    parameters: parameters ?? undefined,
    lmsEntryIn: lmsEntryIn ?? undefined,
    encoreProductCode: encoreProductCode ?? undefined,
  });
}

export function buildBorrowerTermsRowsFromMap(raw: Record<string, unknown> | null | undefined): ProgramDetailRow[] {
  if (!raw) return [];
  return buildBorrowerTermsRows({
    interestRate: raw.interestRate != null ? Number(raw.interestRate) : null,
    discountMarginPercent: raw.discountMarginPercent != null ? Number(raw.discountMarginPercent) : null,
    creditPeriodDays: raw.creditPeriodDays != null ? Number(raw.creditPeriodDays) : null,
    discountHold: raw.discountHold != null ? String(raw.discountHold) : null,
    paymentMethod: raw.paymentMethod != null ? String(raw.paymentMethod) : null,
    overdueInterestRate: raw.overdueInterestRate != null ? Number(raw.overdueInterestRate) : null,
  });
}
