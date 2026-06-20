# Demo data reset (PLP + LOS)

## UAT configuration

Enable reset on sandbox/UAT only — never in production.

| App | Services | Env var | Value |
|-----|----------|---------|-------|
| **PLP** | `iam-service`, `program-service`, `lending-service` | `PLP_DEV_RESET_ENABLED` | `true` |
| **LOS** | `los-core-service` | `LOS_DEMO_ENABLED` | `true` |

Local Docker defaults `PLP_DEV_RESET_ENABLED` to `true`. Set `PLP_DEV_RESET_ENABLED=false` in production.

## PLP reset (platform UI)

**Who:** `PLATFORM_ADMIN` only (Dashboard or Programs page).

**What is removed:** loans, disbursements, repayments, LMS operation logs, programs, sub-programs, anchors, borrowers, invoices, salary data, LOS sync audit, program/lending audit events, and IAM users **except** seeded sandbox accounts.

**What is kept:** Flyway seed users (see `plp.dev-reset.preserved-user-emails` in IAM `application.yml`), report definitions, notification templates.

**Does not** touch the LOS database.

**API order** (used by the UI button):

1. `POST /api/v1/dev/reset-lending-data`
2. `POST /api/v1/dev/reset-program-data`
3. `POST /api/v1/dev/reset-users`

## LOS reset (LOS UI)

**What is removed:** all loan applications and dependents, auto-provisioned `BORROWER` users, and **LOS-local** PLP master rows (`sub_program_masters`, `program_masters`, `anchor_masters`).

**What is kept:** staff seed users, workflow config.

**Does not** call the PLP app or delete remote PLP programs/loans.

**API:** `DELETE /api/v1/demo/applications` (requires `los.demo.enabled=true` or `local` profile).

## Full integrated wipe (LOS + PLP)

Use **both** reset buttons when testing end-to-end:

1. **PLP** — clears remote lending/program data and portal users
2. **LOS** — clears applications, LOS borrowers, and PLP master cache on LOS

Recommended order: **PLP first**, then **LOS**, so you do not re-sync into a dirty PLP environment.
