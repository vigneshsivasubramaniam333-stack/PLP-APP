import type { AuthUser, UserRole } from '../types';

export function getStoredAuthUser(): AuthUser | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  try {
    const raw = localStorage.getItem(window.__PLP_USER_KEY__ ?? 'plp_user');
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

/** Headers required by lending-service loan sanction / disbursement endpoints. */
export function lenderLoanActionHeaders(): Record<string, string> {
  const u = getStoredAuthUser();
  if (!u?.userId || !u?.role) {
    return {};
  }
  return {
    'X-User-Id': u.userId,
    'X-User-Roles': u.role,
  };
}

/** Tenant headers for anchor portal calls ({@code AnchorPortalController}). */
export function anchorAccessHeaders(): Record<string, string> {
  const user = getStoredAuthUser();
  if (!user) {
    return {};
  }
  const h: Record<string, string> = {
    'X-User-Roles': user.role,
    'X-Linked-Entity-Id': user.linkedEntityId ?? '',
    'X-Linked-Entity-Type': user.linkedEntityType ?? '',
  };
  if (user.userId) {
    h['X-User-Id'] = user.userId;
  }
  return h;
}

/** Tenant headers for borrower portal calls (program-service / lending-service guards). */
export function borrowerAccessHeaders(): Record<string, string> {
  const user = getStoredAuthUser();
  if (!user) {
    return {};
  }
  return {
    'X-User-Roles': user.role,
    'X-Linked-Entity-Id': user.linkedEntityId ?? '',
    'X-Linked-Entity-Type': user.linkedEntityType ?? '',
  };
}

/**
 * Headers for POST /loans/{id}/repay — accounts officer (lender) OR borrower on own loan
 * ({@code LoanAccessGuard.requireRepayAccess}).
 */
export function loanRepayHeaders(): Record<string, string> {
  const u = getStoredAuthUser();
  if (!u?.role) {
    return {};
  }
  if ((u.linkedEntityType ?? '').trim().toUpperCase() === 'BORROWER') {
    const h: Record<string, string> = {
      'X-User-Roles': u.role,
      'X-Linked-Entity-Id': u.linkedEntityId ?? '',
      'X-Linked-Entity-Type': u.linkedEntityType ?? '',
    };
    if (u.userId) {
      h['X-User-Id'] = u.userId;
    }
    return h;
  }
  return lenderLoanActionHeaders();
}

/** Headers forwarded to program-service invoice endpoints ({@code InvoiceAccessGuard}). */
export function invoiceAccessHeaders(): Record<string, string> {
  const u = getStoredAuthUser();
  if (!u?.role) {
    return {};
  }
  const h: Record<string, string> = { 'X-User-Roles': u.role };
  if (u.linkedEntityId) {
    h['X-Linked-Entity-Id'] = u.linkedEntityId;
  }
  if (u.linkedEntityType) {
    h['X-Linked-Entity-Type'] = u.linkedEntityType;
  }
  return h;
}

export function lenderLoanCapabilities(role: UserRole | undefined): {
  canSanctionOrReject: boolean;
  canInitiateDisburse: boolean;
  canApproveDisburse: boolean;
  canCancelDisburse: boolean;
  canRecordRepayment: boolean;
  canCreateProgramArtifacts: boolean;
  canApproveProgramArtifacts: boolean;
  canEditProgramConfig: boolean;
} {
  const admin = role === 'PLATFORM_ADMIN';
  return {
    canSanctionOrReject: admin || role === 'CREDIT_ANALYST',
    canInitiateDisburse: admin || role === 'ACCOUNTS_OFFICER',
    canApproveDisburse: admin || role === 'ACCOUNTS_MANAGER',
    canCancelDisburse: admin || role === 'ACCOUNTS_MANAGER',
    canRecordRepayment: admin || role === 'ACCOUNTS_OFFICER',
    canCreateProgramArtifacts: admin || role === 'CREDIT_ANALYST',
    canApproveProgramArtifacts: admin || role === 'CREDIT_MANAGER',
    canEditProgramConfig: admin || role === 'CREDIT_ANALYST' || role === 'CREDIT_MANAGER',
  };
}
