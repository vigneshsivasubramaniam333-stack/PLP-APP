# PayU Payment Gateway (Invoice Discounting)

## Overview

Borrowers enrolled on a sub-program with `paymentMethod = PAYU_PG` repay financed invoices via a **server-side payment cart** and **PayU** browser redirect. Successful PG collection creates **PRUS/PIP** (payment in progress); PLP admin **settlements** post real loan repayments.

Personal loans and `SMART_COLLECT` borrowers continue using direct `POST /api/v1/loans/{id}/repay`.

## Required environment variables

| Variable | Description |
|----------|-------------|
| `PAYU_MERCHANT_KEY` | PayU merchant key (test or live) |
| `PAYU_MERCHANT_SALT` | PayU salt for SHA-512 hash (use **Salt** from PayU dashboard, not Merchant ID) |
| `PAYU_GATEWAY_URL` | e.g. `https://test.payu.in/_payment` |
| `PLP_PUBLIC_API_BASE_URL` | Public API base for PayU surl/furl (must be reachable by the **user's browser** after PayU redirect), e.g. `http://localhost:8180` locally or `https://host/plp-api` in sandbox |
| `PLP_BORROWER_UI_URL` | PLP borrower portal base, e.g. `http://localhost:5174/plp-borrower` |
| `LOS_BORROWER_UI_URL` | LOS borrower portal base **including** the Vite base path, e.g. `http://localhost:5173/los/borrower` (not `/borrower` alone) |

Configure in **lending-service** (`plp.payu.*` in `application.yml`). For Docker, set the same variables on the `lending-service` container (see `docker-compose.yml`).

**Credinnov EC2:** use `docker-compose.sandbox.yml` or copy `.env.example` → `.env` so `PLP_PUBLIC_API_BASE_URL` is `http://credinnov-sandbox.senseitech.com/plp-api` (not `localhost:8180`). Invoice discounting PayU initiated from the **LOS** borrower portal still hits PLP lending-service for surl/furl.

**Reverse hash:** PayU success callbacks are verified with  
`sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)`  
(`status||||||` is 6 pipes = **5** empty fields; `udf1` carries `borrowerId`). If browser callback hash still mismatches, lending-service falls back to PayU **`verify_payment`** API (uses a non–load-balanced HTTP client).

**Local dev with `start-all.ps1`:** gateway is on **8180**, not 8080. Set `PLP_PUBLIC_API_BASE_URL=http://localhost:8180` (already in `application-local.yml` and `start-all.ps1` for lending-service).

## PayU dashboard

Whitelist callback URLs:

- `{PLP_PUBLIC_API_BASE_URL}/api/v1/webhooks/payments/payu/success`
- `{PLP_PUBLIC_API_BASE_URL}/api/v1/webhooks/payments/payu/failure`

## API routes (via gateway)

| Path | Service | Auth |
|------|---------|------|
| `/api/v1/portal/borrower/payments/checkout/**` | lending-service | Borrower JWT or lender proxy (`borrowerId` query) |
| `/api/v1/portal/borrower/payments/payu/initiate` | lending-service | Same |
| `/api/v1/webhooks/payments/payu/success\|failure` | integration-service → lending-service | Open (no JWT) |
| `/api/v1/payments/settlements/**` | lending-service | Accounts Officer / Platform Admin |

## Flow

1. Borrower adds financed invoices to cart.
2. `POST .../payu/initiate` returns hash + PayU form fields.
3. Browser POSTs to PayU; callbacks hit integration-service webhooks.
4. On success: cart lines → `PAID`, `payment_in_progress` rows created, invoice `pip_amount` updated. **No** `recordRepayment` yet.
5. Admin selects open PIP lines, enters settlement UTR → `recordSettlementRepayment` with mode `PAYU_PG_SETTLED`.

## Enabling PayU for a borrower

Repayment mechanism is resolved in this order (highest priority first):

1. **Borrower enrollment override** — `payment_method_mode = CUSTOM` uses the enrollment `payment_method` (`SMART_COLLECT` or `PAYU_PG`).
2. **Platform global default** — `payment_method_mode = GLOBAL` uses `plp_program.product_repayment_defaults` for the parent program’s `product_type` (`PAY_DAY_LOAN`, `INVOICE_DISCOUNTING`).
3. **System fallback** — `SMART_COLLECT` when no global row exists or the global row is disabled.

Existing enrollments default to **CUSTOM**, so behavior is unchanged until an admin edits global defaults and/or switches enrollments to **GLOBAL**.

### Admin configuration

| Portal | Location | Scope |
|--------|----------|-------|
| **PLP Platform Admin** | **Lending → Repayment defaults** (`/repayment-defaults`) | Global default per PLP product type |
| **PLP Platform Admin** | **Sub Programs** / **Borrowers** enrollment form | Per-borrower **Use platform default** (GLOBAL) vs **Custom** (CUSTOM) |
| **LOS Admin** | **Configuration → Repayment defaults** (`/repayment-config`) | Global default per LOS loan product (config only in v1; borrower repay UI not wired yet) |

**Invoice discounting via LOS:** `BUSINESS_WC_INVOICE_DISCOUNTING` is not stored in LOS defaults. Configure it under PLP **Repayment defaults → Invoice Discounting**. LOS invoice discounting borrower flows proxy PLP program-service at runtime.

### LOS vs PLP PayU (scope)

| Flow | Config location | Borrower repay | Admin settlement |
|------|-----------------|----------------|------------------|
| **Invoice discounting** (financed invoices) | PLP **Repayment defaults** → Invoice Discounting | LOS borrower → PLP cart / PayU (proxied) | **PLP Platform Admin → PG settlements (PRUS)** |
| **LOS personal / term loans** | LOS **Configuration → Repayment defaults** | LOS borrower loan account → **Pay via PayU** | **LOS Operations → PG settlements** |
| **Invoice discounting** (financed invoices) | PLP **Repayment defaults** → Invoice Discounting | LOS borrower → Invoice Discounting → PLP cart / PayU | **PLP Platform Admin → PG settlements (PRUS)** |

LOS **Repayment defaults** drive PayU on the borrower **loan account** page for `PERSONAL_LOAN`, `TERM_LOAN`, etc. Invoice discounting must use PLP configuration; the LOS row for `BUSINESS_WC_INVOICE_DISCOUNTING` is intentionally absent.

**LOS PayU env (los-core-service):** `PAYU_MERCHANT_KEY`, `PAYU_MERCHANT_SALT`, `PAYU_GATEWAY_URL`, `LOS_PUBLIC_API_BASE_URL` (PayU callback base, e.g. `http://localhost:8083` or gateway `http://localhost:8080`), `LOS_BORROWER_UI_URL` (e.g. `http://localhost:5173/los/borrower`).

PayU dashboard whitelist for LOS loan repayments:
- `{LOS_PUBLIC_API_BASE_URL}/api/v1/webhooks/los-payments/payu/success`
- `{LOS_PUBLIC_API_BASE_URL}/api/v1/webhooks/los-payments/payu/failure`

**Quick setup (invoice discounting + PayU)**

1. PLP Platform Admin → **Repayment defaults** → set **Invoice Discounting** to **PayU (Payment Gateway)** (optional PG provider code `PAYU`).
2. On a borrower enrollment, choose **Use platform default** (GLOBAL), or **Custom** and set **PayU** explicitly.
3. Borrower portal shows the payment cart when the **resolved** effective method is `PAYU_PG`.
