import type { AuthUser, Invoice, Program, SubProgram } from '@plp/shared';

export const NO_LINKED_BORROWERS =
  'No linked borrowers for this sub-program. Ask your lender to add the counterparty borrower to this sub-program.';

export const CONFIRM_SELF_UPLOAD_TOOLTIP = 'You cannot approve an invoice uploaded by you';

export const inputCls = 'bt-input w-full';
export const labelCls = 'bt-label';

/** Sample CSV for anchor invoice batch upload (named columns). */
export const INVOICE_CSV_SAMPLE = `invoiceNumber,partyCode,invoiceDate,dueDate,invoiceAmount,taxAmount,subProgramCode
INV-2026-001,PTY-001,2026-06-01,2026-08-01,100000,18000,
INV-2026-002,BORR-1001,2026-06-05,2026-09-05,250000,45000,
`;

export function downloadSampleInvoiceCsv(): void {
  const blob = new Blob([INVOICE_CSV_SAMPLE], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'invoice-upload-sample.csv';
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

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
