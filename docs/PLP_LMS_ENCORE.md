# PLP LMS (Encore) integration

## Deploy order

1. Apply Flyway: `program-service` V20, `lending-service` V8
2. Deploy `program-service` (LMS fields on `programs`)
3. Deploy `lending-service` (Encore client + `PlpLmsOrchestrator`)
4. Apply LOS Flyway V56 and deploy `los-core-service` + `ui-service`

## Environment (lending-service)

Mirror LOS Encore settings:

- `ENCORE_BASE_URL`, `ENCORE_API_USERNAME`, `ENCORE_API_PASSWORD`
- `ENCORE_ADMIN_BRANCH`, `ENCORE_CURRENCY` (optional)

Configured under `plp.lms.encore.*` in `lending-service` `application.yml`.

## Program configuration

- LOS Program Setup UI: **Bill discounting flow**, **LMS entry**, **Encore product code**
- Synced to PLP `programs.lms_entry_in` / `encore_product_code`
- When `lms_entry_in = NO`, PLP keeps internal `loanNumber` and local interest/repayment math
- When `YES`, Encore account id is stored in `loans.lms_account_id` and `kfsData.lmsAccountId`

## LOS application LMS

- Gated by `program_masters.lms_entry_in` when application has `sub_program_id`
- Invoice discounting uses `program_masters.encore_product_code` instead of hardcoded `IPPOPAYM01`
