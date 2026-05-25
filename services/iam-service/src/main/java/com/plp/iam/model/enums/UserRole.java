package com.plp.iam.model.enums;

/**
 * IAM roles for PLP.
 *
 * <p><b>Lender-side business mapping:</b>
 * <ul>
 *   <li>{@link #PLATFORM_ADMIN} — Lender Admin</li>
 *   <li>{@link #CREDIT_ANALYST} — Credit Officer / Credit Maker</li>
 *   <li>{@link #CREDIT_MANAGER} — Credit Manager / Credit Checker</li>
 *   <li>{@link #ACCOUNTS_OFFICER} — Accountant / Accounts Maker</li>
 *   <li>{@link #ACCOUNTS_MANAGER} — Accounts Checker</li>
 *   <li>{@link #COMPLIANCE_OFFICER} — Compliance / Audit viewer</li>
 * </ul>
 *
 * <p>{@link #ANCHOR_ADMIN}, {@link #ANCHOR_MAKER}, and {@link #ANCHOR_CHECKER} are tenant-scoped anchor portal roles
 * (linked to an anchor in IAM). {@link #BORROWER} is the borrower portal role.
 */
public enum UserRole {
    PLATFORM_ADMIN,
    CREDIT_MANAGER,
    CREDIT_ANALYST,
    ANCHOR_ADMIN,
    ANCHOR_MAKER,
    ANCHOR_CHECKER,
    BORROWER,
    ACCOUNTS_OFFICER,
    ACCOUNTS_MANAGER,
    COMPLIANCE_OFFICER
}
