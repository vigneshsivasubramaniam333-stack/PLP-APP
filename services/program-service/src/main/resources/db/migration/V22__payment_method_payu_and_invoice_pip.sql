ALTER TABLE plp_program.sub_program_borrowers
    DROP CONSTRAINT IF EXISTS chk_spb_payment_method;

ALTER TABLE plp_program.sub_program_borrowers
    ADD CONSTRAINT chk_spb_payment_method CHECK (payment_method IN ('SMART_COLLECT', 'PAYU_PG'));

ALTER TABLE plp_program.invoices
    ADD COLUMN IF NOT EXISTS pip_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pip_discount_amount NUMERIC(15, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN plp_program.invoices.pip_amount IS 'PRUS/PIP principal — payment received via PG, pending lender settlement';
COMMENT ON COLUMN plp_program.invoices.pip_discount_amount IS 'PRUS/PIP cash-discount component pending settlement';
