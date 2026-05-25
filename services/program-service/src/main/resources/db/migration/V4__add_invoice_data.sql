-- V4__add_invoice_data.sql
-- Invoice data for Invoice Discounting product

CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number      VARCHAR(50) NOT NULL,
    anchor_id           UUID NOT NULL REFERENCES anchors(id),
    borrower_id         UUID NOT NULL REFERENCES borrowers(id),
    program_id          UUID NOT NULL REFERENCES programs(id),

    invoice_date        DATE NOT NULL,
    due_date            DATE NOT NULL,
    invoice_amount      NUMERIC(15,2) NOT NULL,
    tax_amount          NUMERIC(15,2) DEFAULT 0,
    net_amount          NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(3) DEFAULT 'INR',

    -- Three-way match fields
    po_number           VARCHAR(50),
    po_date             DATE,
    po_amount           NUMERIC(15,2),
    grn_number          VARCHAR(50),
    grn_date            DATE,
    three_way_match     BOOLEAN DEFAULT FALSE,

    -- Eligibility
    margin_percent      NUMERIC(5,2),
    eligible_amount     NUMERIC(15,2),
    discounted_amount   NUMERIC(15,2) DEFAULT 0,
    available_amount    NUMERIC(15,2),

    -- Status
    status              VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    -- UPLOADED, VERIFIED, ELIGIBLE, PARTIALLY_DISCOUNTED, FULLY_DISCOUNTED, EXPIRED, REJECTED
    verified            BOOLEAN DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    verified_by         UUID,
    anchor_confirmed    BOOLEAN DEFAULT FALSE,
    anchor_confirmed_at TIMESTAMPTZ,

    -- Source
    source              VARCHAR(20) DEFAULT 'MANUAL',
    -- MANUAL, ERP_SYSTEM
    gstin_seller        VARCHAR(15),
    gstin_buyer         VARCHAR(15),
    payment_terms       VARCHAR(100),
    description         VARCHAR(500),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(invoice_number, anchor_id)
);

CREATE INDEX idx_invoices_anchor ON invoices(anchor_id);
CREATE INDEX idx_invoices_borrower ON invoices(borrower_id);
CREATE INDEX idx_invoices_program ON invoices(program_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
