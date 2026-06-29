export type UserRole =
  | 'PLATFORM_ADMIN'
  | 'CREDIT_MANAGER'
  | 'CREDIT_ANALYST'
  | 'ANCHOR_ADMIN'
  | 'ANCHOR_MAKER'
  | 'ANCHOR_CHECKER'
  | 'BORROWER'
  | 'ACCOUNTS_OFFICER'
  | 'ACCOUNTS_MANAGER'
  | 'COMPLIANCE_OFFICER';

export type ProductType = 'PAY_DAY_LOAN' | 'INVOICE_DISCOUNTING';

export type ProgramStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED';

export type LoanStatus =
  | 'REQUESTED'
  | 'ELIGIBILITY_CHECK'
  | 'SANCTIONED'
  | 'REJECTED'
  | 'DISBURSEMENT_PENDING'
  | 'DISBURSED'
  | 'REPAYMENT_DUE'
  | 'OVERDUE'
  | 'CLOSED'
  | 'CANCELLED'
  | 'WRITTEN_OFF';

export interface AuthUser {
  userId: string;
  email: string;
  fullName: string;
  role: UserRole;
  accessToken: string;
  refreshToken: string;
  /** Business anchor/borrower UUID when provisioned on the IAM user */
  linkedEntityId?: string | null;
  linkedEntityType?: string | null;
  /** When true, portal must force password change before continuing. */
  passwordResetRequired?: boolean;
}

/** IAM user row from GET /api/v1/users (PLATFORM_ADMIN only). */
export interface ListedUser {
  id: string;
  email: string;
  fullName: string;
  role: string;
  linkedEntityId: string | null;
  linkedEntityType: string | null;
  status: string | null;
}

/** All roles for admin filters and dropdowns. */
export const ALL_USER_ROLES: readonly UserRole[] = [
  'PLATFORM_ADMIN',
  'CREDIT_MANAGER',
  'CREDIT_ANALYST',
  'ANCHOR_ADMIN',
  'ANCHOR_MAKER',
  'ANCHOR_CHECKER',
  'BORROWER',
  'ACCOUNTS_OFFICER',
  'ACCOUNTS_MANAGER',
  'COMPLIANCE_OFFICER',
];

export interface ProgramEligibilityConfig {
  maxInvoiceAgeDays?: number;
  minInvoiceAmount?: number;
  minDaysToDueDate?: number;
}

export interface ProgramOperationalParameters {
  enablePaymentForBorrower?: boolean;
  autoDiscounting?: boolean;
  discountingDay?: number;
  gapBetweenDiscountingDays?: number;
  gapBetweenPreviousInvoiceDays?: number;
  autoPullOption?: boolean;
  gapBetweenSanctionAndDisbursementDays?: number;
  intFreeCreditPeriod?: boolean;
  intFreePeriodDays?: number;
  autoPaymentBorrower?: boolean;
  autoAcceptInvoices?: boolean;
  sanctionType?: 'MANUAL' | 'AUTO' | string;
  partialDiscount?: boolean;
  invoiceDelete?: boolean;
}

export interface Program {
  id: string;
  programCode: string;
  programName: string;
  description?: string | null;
  productType: ProductType;
  /** Umbrella programs may omit; anchor association is per sub-program. */
  anchorId?: string | null;
  lenderId: string;
  programLimit: number;
  anchorLimit?: number | null;
  maxBorrowerLimit: number;
  defaultInterestRate: number;
  /** Margin deducted from net to get eligible amount. 0 means full eligibility. */
  marginPercent?: number | null;
  maxTenureDays: number;
  status: ProgramStatus;
  createdAt?: string | null;
  /** From program borrower limits aggregation (listing / GET). */
  utilizedLimit?: number | null;
  /** programLimit − utilizedLimit (floored at 0). */
  availableLimit?: number | null;
  /** Server merges partial eligibility-related entries on PUT. */
  config?: ProgramEligibilityConfig | Record<string, unknown> | null;
  parameters?: ProgramOperationalParameters | Record<string, unknown> | null;
  lmsEntryIn?: 'YES' | 'NO' | string | null;
  encoreProductCode?: string | null;
}

export type SubProgramStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE';

export interface SubProgram {
  id: string;
  programId: string;
  anchorId: string;
  name: string;
  code: string;
  flowType: string;
  anchorRole: string;
  borrowerRole: string;
  interestRate: number | null;
  marginPercent: number | null;
  maxTenureDays: number | null;
  subProgramLimit: number | null;
  utilizedLimit: number | null;
  availableLimit: number | null;
  status: SubProgramStatus | string;
  /** YES/NO — SBD Early Pay master switch (only meaningful for SALES_BILL_DISCOUNTING). */
  allowEarlyPay?: string | null;
  createdAt?: string | null;
}

export interface SubProgramBorrower {
  id: string;
  subProgramId: string;
  borrowerId: string;
  borrowerLimit: number | null;
  utilizedLimit: number | null;
  availableLimit: number | null;
  status: string;
  interestRate?: number | null;
  discountMarginPercent?: number | null;
  creditPeriodDays?: number | null;
  discountHold?: 'YES' | 'NO' | string | null;
  paymentMethod?: string | null;
  paymentMethodMode?: 'GLOBAL' | 'CUSTOM' | string | null;
  overdueInterestRate?: number | null;
  partyCode?: string | null;
  borrowerOdAccountNumber?: string | null;
  borrowerOdBankName?: string | null;
  borrowerOdBankIfsc?: string | null;
  borrowerOdAccountName?: string | null;
  idfcCollectionAccountName?: string | null;
  idfcOdAccountNumber?: string | null;
  idfcIfscCode?: string | null;
  idfcUpiId?: string | null;
  castlerEscrowAccountIn?: string | null;
  castlerEscrowAccountId?: string | null;
  castlerEscrowPayeeId?: string | null;
  razorpayRouteAccountId?: string | null;
  razorpaySmartCollectAcId?: string | null;
  razorpayFee?: number | null;
  hdfcAccountNo?: string | null;
  hdfcIfscCode?: string | null;
}

export interface ProductRepaymentDefault {
  productType: ProductType;
  repaymentMechanism: string;
  pgProviderCode?: string | null;
  enabled: boolean;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface Anchor {
  id: string;
  anchorCode: string;
  entityName: string;
  entityType: string;
  gstin: string | null | undefined;
  status: string;
  contactPersonName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  createdAt?: string | null;
}

export interface Borrower {
  id: string;
  borrowerCode: string;
  name: string;
  /** Program this borrower belongs to */
  programId: string;
  anchorId: string;
  email?: string | null;
  phone?: string | null;
  status: string;
  createdAt?: string | null;
}

export interface BorrowerLimit {
  id: string;
  borrowerId: string;
  programId: string;
  sanctionedLimit: number;
  utilizedLimit: number;
  availableLimit: number;
  status: string;
}

export interface Loan {
  id: string;
  loanNumber: string;
  borrowerId: string;
  programId: string;
  invoiceId?: string | null;
  productType: string;
  requestedAmount: number;
  sanctionedAmount: number;
  disbursedAmount: number;
  interestRate: number;
  tenureDays: number;
  totalRepayable: number;
  totalRepaid?: number | null;
  outstandingAmount: number;
  status: LoanStatus;
  requestDate: string;
  sanctionDate?: string | null;
  dueDate: string;
  /** ISO timestamp when the loan row was created (lending-service). */
  createdAt?: string | null;
  /** Encore LMS account id when program lms_entry_in=YES. */
  lmsAccountId?: string | null;
  kfsData?: Record<string, unknown> | null;
  /** Lending-service may store pending disburse amount here between initiate and execute. */
  eligibilitySnapshot?: Record<string, unknown> | null;
}

export interface EmployeeSalaryData {
  id: string;
  borrowerId: string;
  anchorId: string;
  programId: string;
  employeeCode: string;
  payPeriod: string;
  /** System-assigned display number (e.g. SSL-0000000001) */
  salarySlipNumber?: string;
  externalReferenceNumber?: string | null;
  periodFrom?: string;
  periodTo?: string;
  /** Persisted lifecycle from program-service */
  slipStatus?:
    | 'AVAILABLE_FOR_LOAN'
    | 'LOAN_REQUESTED'
    | 'REJECTED_AVAILABLE_AGAIN'
    | 'DISBURSED_USED'
    | 'CLOSED_USED';
  grossSalary: number;
  netSalary: number;
  daysWorked: number;
  totalWorkingDays: number;
  accumulatedSalary: number;
  deductions: number;
  eligibleAmount: number;
  eligibilityPercent: number;
  source: string;
  verified: boolean;
}

export type InvoiceStatus =
  | 'UPLOADED'
  | 'VERIFIED'
  | 'ELIGIBLE'
  | 'BORROWER_ACCEPTED'
  | 'FINANCING_REQUESTED'
  | 'PARTIALLY_DISCOUNTED'
  | 'FULLY_DISCOUNTED'
  | 'EXPIRED'
  | 'REJECTED'
  | 'CLOSED'
  | 'DISCOUNTED_EP'
  | 'SANCTIONED_EP';

export interface Invoice {
  id: string;
  invoiceNumber: string;
  anchorId: string;
  borrowerId: string;
  programId: string;
  subProgramId?: string | null;
  invoiceDate: string;
  dueDate: string;
  invoiceAmount: number;
  taxAmount: number;
  netAmount: number;
  currency: string;
  poNumber?: string;
  poDate?: string;
  poAmount?: number;
  grnNumber?: string;
  grnDate?: string;
  threeWayMatch: boolean;
  marginPercent: number;
  eligibleAmount: number;
  discountedAmount: number;
  availableAmount: number;
  status: InvoiceStatus;
  verified: boolean;
  anchorConfirmed: boolean;
  source: string;
  /** Purchase vs sales bill discounting; empty/undefined treated as purchase flow */
  flowType?: string | null;
  borrowerAccepted?: boolean;
  borrowerAcceptedAt?: string | null;
  /** IAM user id who uploaded the invoice (anchor portal); omitted on legacy rows */
  uploadedByUserId?: string | null;
  gstinSeller?: string;
  gstinBuyer?: string;
  paymentTerms?: string;
  description?: string;
  /** Original digital invoice filename when attached via anchor portal */
  digitalInvoiceFileName?: string | null;
  digitalInvoiceContentType?: string | null;
  digitalInvoiceUploadedAt?: string | null;
  /** Payment in progress (PRUS) after PayU success, pending admin settlement */
  pipAmount?: number;
  pipDiscountAmount?: number;
  /** SBD Early Pay list enrichment */
  isEarlyPayAllowed?: string | null;
  showEarlyPay?: string | null;
  balDueAmount?: number | null;
  /** Early Pay payout/repayment amount from the approved EP request */
  earlyPayRequestedAmount?: number | null;
}

export interface InvoicePageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LoanRepaymentRow {
  id: string;
  amount: number;
  paidAt?: string | null;
  paymentMode?: string | null;
  reference?: string | null;
  status?: string | null;
}

export interface EligibilityResult {
  borrowerId: string;
  programId: string;
  requestedAmount: number;
  eligible: boolean;
  eligibleAmount: number;
  activeLoans: number;
  reasons: string[];
  /** Salary slip id used for evaluation (UUID string when present). */
  salaryDataId?: string | null;
  /** Persisted slip status from program-service when present. */
  slipStatus?: string | null;
  derivedSalaryStatus?: string | null;
}

export interface ApiResponse<T> {
  status: 'SUCCESS' | 'ERROR';
  data: T;
  message?: string;
}
