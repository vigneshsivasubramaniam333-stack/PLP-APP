-- SBD Early Pay: sub-program flags, daily parameters, requests

ALTER TABLE plp_program.sub_programs
    ADD COLUMN IF NOT EXISTS allow_early_pay VARCHAR(3) NOT NULL DEFAULT 'NO';

ALTER TABLE plp_program.sub_program_borrowers
    ADD COLUMN IF NOT EXISTS enable_early_pay VARCHAR(3) NOT NULL DEFAULT 'NO';

CREATE TABLE IF NOT EXISTS plp_program.early_pay_parameters (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_program_id          UUID NOT NULL REFERENCES plp_program.sub_programs(id),
    anchor_id               UUID NOT NULL REFERENCES plp_program.anchors(id),
    ep_date                 DATE NOT NULL,
    discount_percentage     NUMERIC(7,4) NOT NULL,
    ep_amount               NUMERIC(19,2) NOT NULL,
    total_margin_amount     NUMERIC(19,2) NOT NULL DEFAULT 0,
    consumed_margin_amount  NUMERIC(19,2) NOT NULL DEFAULT 0,
    un_allocated_amount     NUMERIC(19,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remarks                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sub_program_id, ep_date)
);

CREATE INDEX IF NOT EXISTS idx_ep_parameters_anchor_date
    ON plp_program.early_pay_parameters(anchor_id, ep_date);

CREATE TABLE IF NOT EXISTS plp_program.early_pay_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ep_parameter_id     UUID NOT NULL REFERENCES plp_program.early_pay_parameters(id),
    invoice_id          UUID NOT NULL REFERENCES plp_program.invoices(id),
    borrower_id         UUID NOT NULL REFERENCES plp_program.borrowers(id),
    sub_program_id      UUID NOT NULL REFERENCES plp_program.sub_programs(id),
    invoice_no          VARCHAR(50) NOT NULL,
    invoice_amount      NUMERIC(15,2) NOT NULL,
    requested_amount    NUMERIC(15,2) NOT NULL,
    cd_amount           NUMERIC(15,2),
    cd_percentage       NUMERIC(7,4),
    ep_date             DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    remarks             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ep_requests_invoice ON plp_program.early_pay_requests(invoice_id);
CREATE INDEX IF NOT EXISTS idx_ep_requests_status ON plp_program.early_pay_requests(status);
CREATE INDEX IF NOT EXISTS idx_ep_requests_sub_program ON plp_program.early_pay_requests(sub_program_id);
