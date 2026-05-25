-- V1__init_program_schema.sql
-- Program Service — Programs, Anchors, Borrowers, Limits

-- Anchors (Employers for PDL, Sellers for Invoice Discounting)
CREATE TABLE anchors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anchor_code         VARCHAR(20) NOT NULL UNIQUE,
    entity_name         VARCHAR(200) NOT NULL,
    entity_type         VARCHAR(30) NOT NULL,
    gstin               VARCHAR(15),
    pan                 VARCHAR(10),
    cin                 VARCHAR(21),
    address             JSONB,
    bank_account        JSONB,
    integration_config  JSONB,
    contact_person_name VARCHAR(100),
    contact_email       VARCHAR(100),
    contact_phone       VARCHAR(15),
    agreement_doc_id    UUID,
    rating              VARCHAR(5),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Programs (Anchor + Lender + Product combination)
CREATE TABLE programs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_code                VARCHAR(30) NOT NULL UNIQUE,
    program_name                VARCHAR(200) NOT NULL,
    product_type                VARCHAR(30) NOT NULL,
    anchor_id                   UUID NOT NULL REFERENCES anchors(id),
    lender_id                   UUID NOT NULL,
    program_limit               NUMERIC(15,2) NOT NULL,
    anchor_limit                NUMERIC(15,2) NOT NULL,
    max_borrower_limit          NUMERIC(15,2) NOT NULL,
    interest_rate_min           NUMERIC(5,2),
    interest_rate_max           NUMERIC(5,2),
    default_interest_rate       NUMERIC(5,2),
    max_tenure_days             INTEGER,
    margin_percent              NUMERIC(5,2),
    processing_fee_percent      NUMERIC(5,2),
    penal_rate_percent          NUMERIC(5,2),
    grace_period_days           INTEGER DEFAULT 3,
    auto_approve_threshold      NUMERIC(15,2),
    max_concurrent_loans        INTEGER DEFAULT 1,
    cooling_off_days            INTEGER DEFAULT 3,
    eligibility_refresh_days    INTEGER DEFAULT 30,
    concentration_limit_percent NUMERIC(5,2) DEFAULT 30.00,
    eligibility_rules           JSONB,
    parameters                  JSONB,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    valid_from                  DATE,
    valid_to                    DATE,
    auto_renewal                BOOLEAN DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Borrowers (Employees for PDL, Buyers for Invoice Discounting)
CREATE TABLE borrowers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_code       VARCHAR(20) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    program_id          UUID NOT NULL REFERENCES programs(id),
    anchor_id           UUID NOT NULL REFERENCES anchors(id),
    email               VARCHAR(100),
    phone               VARCHAR(15),
    pan                 VARCHAR(10),
    aadhaar_hash        VARCHAR(12),
    gstin               VARCHAR(15),
    personal_info       JSONB,
    bank_account        JSONB,
    employment_info     JSONB,
    kyc_status          VARCHAR(30),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING_KYC',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Borrower Limits (per borrower per program)
CREATE TABLE borrower_limits (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id         UUID NOT NULL REFERENCES borrowers(id),
    program_id          UUID NOT NULL REFERENCES programs(id),
    sanctioned_limit    NUMERIC(15,2) NOT NULL,
    utilized_limit      NUMERIC(15,2) NOT NULL DEFAULT 0,
    available_limit     NUMERIC(15,2) NOT NULL,
    overdue_amount      NUMERIC(15,2) DEFAULT 0,
    interest_rate       NUMERIC(5,2),
    max_concurrent_loans INTEGER DEFAULT 1,
    active_loan_count   INTEGER DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_evaluated_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(borrower_id, program_id)
);

-- Indexes
CREATE INDEX idx_programs_anchor ON programs(anchor_id);
CREATE INDEX idx_programs_product_type ON programs(product_type);
CREATE INDEX idx_programs_status ON programs(status);
CREATE INDEX idx_anchors_status ON anchors(status);
CREATE INDEX idx_anchors_gstin ON anchors(gstin);
CREATE INDEX idx_borrowers_program ON borrowers(program_id);
CREATE INDEX idx_borrowers_anchor ON borrowers(anchor_id);
CREATE INDEX idx_borrowers_pan ON borrowers(pan);
CREATE INDEX idx_borrowers_status ON borrowers(status);
CREATE INDEX idx_limits_borrower ON borrower_limits(borrower_id);
CREATE INDEX idx_limits_program ON borrower_limits(program_id);
CREATE INDEX idx_limits_status ON borrower_limits(status);
