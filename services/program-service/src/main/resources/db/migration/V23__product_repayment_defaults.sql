CREATE TABLE IF NOT EXISTS plp_program.product_repayment_defaults (
    product_type VARCHAR(30) PRIMARY KEY,
    repayment_mechanism VARCHAR(30) NOT NULL DEFAULT 'SMART_COLLECT',
    pg_provider_code VARCHAR(30),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100)
);

ALTER TABLE plp_program.product_repayment_defaults
    ADD CONSTRAINT chk_prd_repayment_mechanism
        CHECK (repayment_mechanism IN ('SMART_COLLECT', 'PAYU_PG', 'API_PG'));

INSERT INTO plp_program.product_repayment_defaults (product_type, repayment_mechanism, enabled)
VALUES ('PAY_DAY_LOAN', 'SMART_COLLECT', TRUE),
       ('INVOICE_DISCOUNTING', 'SMART_COLLECT', TRUE)
ON CONFLICT (product_type) DO NOTHING;

ALTER TABLE plp_program.sub_program_borrowers
    ADD COLUMN IF NOT EXISTS payment_method_mode VARCHAR(10) NOT NULL DEFAULT 'CUSTOM';

ALTER TABLE plp_program.sub_program_borrowers
    DROP CONSTRAINT IF EXISTS chk_spb_payment_method_mode;

ALTER TABLE plp_program.sub_program_borrowers
    ADD CONSTRAINT chk_spb_payment_method_mode
        CHECK (payment_method_mode IN ('GLOBAL', 'CUSTOM'));

COMMENT ON COLUMN plp_program.sub_program_borrowers.payment_method_mode IS
    'GLOBAL = use product_repayment_defaults for program product type; CUSTOM = use payment_method on this row';
