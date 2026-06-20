ALTER TABLE plp_program.sub_program_borrowers
    ADD COLUMN IF NOT EXISTS interest_rate NUMERIC(7, 4),
    ADD COLUMN IF NOT EXISTS discount_margin_percent NUMERIC(7, 4),
    ADD COLUMN IF NOT EXISTS credit_period_days INTEGER,
    ADD COLUMN IF NOT EXISTS discount_hold VARCHAR(3) NOT NULL DEFAULT 'NO',
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30) NOT NULL DEFAULT 'SMART_COLLECT',
    ADD COLUMN IF NOT EXISTS overdue_interest_rate NUMERIC(7, 4) NOT NULL DEFAULT 0;

ALTER TABLE plp_program.sub_program_borrowers
    ADD CONSTRAINT chk_spb_discount_hold CHECK (discount_hold IN ('YES', 'NO'));

ALTER TABLE plp_program.sub_program_borrowers
    ADD CONSTRAINT chk_spb_payment_method CHECK (payment_method IN ('SMART_COLLECT'));
