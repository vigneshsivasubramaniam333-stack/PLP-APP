CREATE TABLE IF NOT EXISTS plp_lending.payment_checkout_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id         UUID NOT NULL,
    invoice_id          UUID NOT NULL,
    loan_id             UUID,
    sub_program_id      UUID,
    program_id          UUID NOT NULL,
    invoice_number      VARCHAR(50),
    amount_to_pay       NUMERIC(15, 2) NOT NULL,
    discount_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIALIZED',
    pg_transaction_id   UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_checkout_line_status CHECK (status IN ('INITIALIZED', 'PAID', 'REMOVED'))
);

CREATE INDEX IF NOT EXISTS idx_checkout_borrower_status ON plp_lending.payment_checkout_lines (borrower_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_checkout_borrower_invoice_active
    ON plp_lending.payment_checkout_lines (borrower_id, invoice_id)
    WHERE status = 'INITIALIZED';

CREATE TABLE IF NOT EXISTS plp_lending.payment_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pg_transaction_ref  VARCHAR(100) NOT NULL UNIQUE,
    borrower_id         UUID NOT NULL,
    total_amount        NUMERIC(15, 2) NOT NULL,
    gateway             VARCHAR(30) NOT NULL DEFAULT 'PAYU',
    status              VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    payu_mihpayid       VARCHAR(100),
    portal_source       VARCHAR(20),
    raw_callback_json   JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_txn_status CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'VERIFY_PENDING'))
);

CREATE INDEX IF NOT EXISTS idx_payment_txn_borrower ON plp_lending.payment_transactions (borrower_id, status);

CREATE TABLE IF NOT EXISTS plp_lending.pg_settlement_batches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_date     DATE NOT NULL,
    settlement_utr      VARCHAR(100) NOT NULL,
    settlement_mode     VARCHAR(30) NOT NULL DEFAULT 'PG',
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_by          VARCHAR(100),
    remarks             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at          TIMESTAMPTZ,
    CONSTRAINT chk_settlement_batch_status CHECK (status IN ('PENDING', 'APPLIED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS plp_lending.payment_in_progress (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pg_transaction_id   UUID NOT NULL REFERENCES plp_lending.payment_transactions(id),
    checkout_line_id    UUID REFERENCES plp_lending.payment_checkout_lines(id),
    invoice_id          UUID NOT NULL,
    loan_id             UUID NOT NULL,
    borrower_id         UUID NOT NULL,
    principal_amount    NUMERIC(15, 2) NOT NULL,
    discount_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    pip_status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    settlement_batch_id UUID REFERENCES plp_lending.pg_settlement_batches(id),
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pip_status CHECK (pip_status IN ('OPEN', 'SETTLED'))
);

CREATE INDEX IF NOT EXISTS idx_pip_open ON plp_lending.payment_in_progress (pip_status, borrower_id);
CREATE INDEX IF NOT EXISTS idx_pip_invoice ON plp_lending.payment_in_progress (invoice_id, pip_status);
