# SBD Early Pay — Legacy Parity Reference

Early Pay applies only to **Sales Bill Discounting (SBD)** seller-initiated invoices. It is anchor-funded dynamic discounting, not lender disbursement.

## Enablement (three layers)

1. **Sub-program (anchor):** `sub_programs.allow_early_pay = YES` (legacy: `debtor.allow_early_pay`)
2. **Borrower enrollment:** `sub_program_borrowers.enable_early_pay = YES` (legacy: `borrower_debtor_link.enable_early_pay`)
3. **Daily parameters:** active `early_pay_parameters` row for sub-program + today's date (legacy: per debtor + date)

Borrower UI shows **Request Early Pay** when invoice status is `ELIGIBLE` (legacy: `Approved`), `isEarlyPayAllowed=YES`, and `showEarlyPay=YES`.

## Amounts

- `balDueAmount` = invoice net amount minus amount already discounted
- `requestedAmount = balDueAmount - (balDueAmount * discountPercentage / 100)`
- `cdAmount = invoiceAmount - requestedAmount`
- Request blocked if `requestedAmount > (totalMarginAmount - consumedMarginAmount)`

## Margin (daily parameter)

- **Request:** `consumedMarginAmount += requestedAmount`
- **Approve:** `consumedMarginAmount -= requestedAmount`; `unAllocatedAmount -= requestedAmount`
- **Reject:** `consumedMarginAmount -= requestedAmount`; invoice reverts to `ELIGIBLE`

## Invoice status flow

| Step | PLP status | Legacy status |
|------|------------|---------------|
| Anchor approved | ELIGIBLE | Approved |
| Borrower requested | DISCOUNTED_EP | DISCOUNTED_EP |
| Anchor approved request | SANCTIONED_EP | SANCTIONED_EP |
| Repayment complete | CLOSED | CLOSED |
| Rejected request | ELIGIBLE | Approved |

## Mutual exclusion

Invoice in `ELIGIBLE` may use **either** lender `requestFinance` **or** Early Pay, not both. Finance is blocked while status is `DISCOUNTED_EP` or `SANCTIONED_EP`.

## Nightly job

Auto-reject all `REQUESTED` early pay requests; revert invoices from `DISCOUNTED_EP` to `ELIGIBLE`; release blocked margin.
