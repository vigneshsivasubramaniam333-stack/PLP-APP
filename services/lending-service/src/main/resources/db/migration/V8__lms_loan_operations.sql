-- LMS (Encore) operation audit / idempotency for PLP loans

ALTER TABLE plp_lending.loans
    ADD COLUMN IF NOT EXISTS lms_account_id VARCHAR(50);

CREATE TABLE IF NOT EXISTS plp_lending.lms_loan_operations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id         UUID NOT NULL REFERENCES plp_lending.loans(id),
    operation       VARCHAR(20) NOT NULL,
    encore_account_id VARCHAR(50),
    status          VARCHAR(20) NOT NULL,
    request_json    TEXT,
    response_json   TEXT,
    error_message   VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lms_loan_operations_loan_id ON plp_lending.lms_loan_operations (loan_id);
CREATE INDEX IF NOT EXISTS idx_lms_loan_operations_loan_op ON plp_lending.lms_loan_operations (loan_id, operation);
