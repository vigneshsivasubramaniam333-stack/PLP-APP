import type { AuthUser, Invoice, Program, SubProgram } from '@plp/shared';

export const NO_LINKED_BORROWERS =
  'No linked borrowers for this sub-program. Ask your lender to add the counterparty borrower to this sub-program.';

export const CONFIRM_SELF_UPLOAD_TOOLTIP = 'You cannot approve an invoice uploaded by you';

export const inputCls =
  'w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 focus:bg-white outline-none';
export const labelCls = 'block text-sm font-medium text-slate-700 mb-1.5';

export function anchorIdFromUser(linkedType: string | null | undefined, linkedId: string | null | undefined): string {
  if ((linkedType ?? '').trim().toUpperCase() !== 'ANCHOR') return '';
  return (linkedId ?? '').trim();
}

export function isInvoiceDiscountingSubProgram(sp: SubProgram, programs: Program[]): boolean {
  const parent = programs.find((p) => p.id === sp.programId);
  return parent?.productType === 'INVOICE_DISCOUNTING';
}

function parseRoleList(role: string): string[] {
  return role
    .split(',')
    .map((r) => r.trim())
    .filter(Boolean);
}

export function isCheckerCannotConfirmOwnUpload(invoice: Invoice, user: AuthUser | null): boolean {
  if (!user) return false;
  const roles = parseRoleList(user.role);
  if (roles.includes('ANCHOR_ADMIN')) return false;
  if (!roles.includes('ANCHOR_CHECKER')) return false;
  const uid = invoice.uploadedByUserId;
  if (uid == null || uid === '') return false;
  return uid === user.userId;
}

export function formatInvoiceCurrency(amount: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(
    amount || 0,
  );
}

export function invoiceStatusBadgeClass(status: string): string {
  const styles: Record<string, string> = {
    UPLOADED: 'bg-slate-50 text-slate-600 ring-1 ring-slate-400/20',
    VERIFIED: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
    ELIGIBLE: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
    PARTIALLY_DISCOUNTED: 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20',
    FULLY_DISCOUNTED: 'bg-purple-50 text-purple-700 ring-1 ring-purple-600/20',
  };
  return styles[status] || 'bg-slate-50 text-slate-600';
}
