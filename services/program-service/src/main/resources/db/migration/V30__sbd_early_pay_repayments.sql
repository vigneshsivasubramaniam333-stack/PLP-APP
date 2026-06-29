-- SBD Early Pay: repayment ledger (one row per recorded repayment against an approved EP request)

CREATE TABLE IF NOT EXISTS plp_program.early_pay_repayments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ep_request_id       UUID NOT NULL REFERENCES plp_program.early_pay_requests(id),
    invoice_id          UUID NOT NULL REFERENCES plp_program.invoices(id),
    borrower_id         UUID NOT NULL REFERENCES plp_program.borrowers(id),
    anchor_id           UUID NOT NULL REFERENCES plp_program.anchors(id),
    sub_program_id      UUID NOT NULL REFERENCES plp_program.sub_programs(id),
    invoice_no          VARCHAR(50) NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    remarks             TEXT,
    repaid_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ep_repayments_anchor ON plp_program.early_pay_repayments(anchor_id);
CREATE INDEX IF NOT EXISTS idx_ep_repayments_borrower ON plp_program.early_pay_repayments(borrower_id);
CREATE INDEX IF NOT EXISTS idx_ep_repayments_invoice ON plp_program.early_pay_repayments(invoice_id);
