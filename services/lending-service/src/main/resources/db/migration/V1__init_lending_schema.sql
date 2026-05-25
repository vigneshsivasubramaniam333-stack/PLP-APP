-- V1__init_lending_schema.sql
-- Lending Service — Loans, Disbursements, Repayments

CREATE TABLE loans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number         VARCHAR(30) NOT NULL UNIQUE,
    borrower_id         UUID NOT NULL,
    program_id          UUID NOT NULL,
    anchor_id           UUID NOT NULL,
    product_type        VARCHAR(30) NOT NULL,
    requested_amount    NUMERIC(15,2) NOT NULL,
    sanctioned_amount   NUMERIC(15,2),
    disbursed_amount    NUMERIC(15,2),
    interest_rate       NUMERIC(5,2),
    interest_method     VARCHAR(20) DEFAULT 'FLAT',
    interest_amount     NUMERIC(15,2),
    processing_fee      NUMERIC(15,2),
    total_repayable     NUMERIC(15,2),
    total_repaid        NUMERIC(15,2) DEFAULT 0,
    outstanding_amount  NUMERIC(15,2) DEFAULT 0,
    penal_interest      NUMERIC(15,2) DEFAULT 0,
    tenure_days         INTEGER NOT NULL,
    request_date        DATE,
    sanction_date       DATE,
    disbursement_date   DATE,
    due_date            DATE,
    closure_date        DATE,
    dpd                 INTEGER DEFAULT 0,
    invoice_id          UUID,
    salary_data_id      UUID,
    eligibility_snapshot JSONB,
    kfs_data            JSONB,
    status              VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    rejection_reason    VARCHAR(500),
    auto_approved       BOOLEAN DEFAULT FALSE,
    approved_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE disbursements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID NOT NULL REFERENCES loans(id),
    disbursement_ref    VARCHAR(30) NOT NULL UNIQUE,
    amount              NUMERIC(15,2) NOT NULL,
    payment_mode        VARCHAR(50),
    utr_number          VARCHAR(100),
    beneficiary_account VARCHAR(50),
    beneficiary_ifsc    VARCHAR(30),
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    initiated_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE repayments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID NOT NULL REFERENCES loans(id),
    repayment_ref       VARCHAR(30) NOT NULL UNIQUE,
    expected_amount     NUMERIC(15,2) NOT NULL,
    paid_amount         NUMERIC(15,2),
    principal_component NUMERIC(15,2),
    interest_component  NUMERIC(15,2),
    penal_component     NUMERIC(15,2),
    due_date            DATE,
    paid_date           DATE,
    payment_mode        VARCHAR(50),
    utr_number          VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_loans_borrower ON loans(borrower_id);
CREATE INDEX idx_loans_program ON loans(program_id);
CREATE INDEX idx_loans_status ON loans(status);
CREATE INDEX idx_loans_due_date ON loans(due_date);
CREATE INDEX idx_loans_loan_number ON loans(loan_number);
CREATE INDEX idx_disbursements_loan ON disbursements(loan_id);
CREATE INDEX idx_disbursements_status ON disbursements(status);
CREATE INDEX idx_repayments_loan ON repayments(loan_id);
CREATE INDEX idx_repayments_due_date ON repayments(due_date);
CREATE INDEX idx_repayments_status ON repayments(status);
