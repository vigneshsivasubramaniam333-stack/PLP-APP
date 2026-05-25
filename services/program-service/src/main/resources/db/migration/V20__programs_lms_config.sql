-- Program-level LMS (Encore) configuration (bl-core lms_entry_in parity)

ALTER TABLE plp_program.programs
    ADD COLUMN IF NOT EXISTS lms_entry_in VARCHAR(3) NOT NULL DEFAULT 'NO',
    ADD COLUMN IF NOT EXISTS encore_product_code VARCHAR(50);

COMMENT ON COLUMN plp_program.programs.lms_entry_in IS 'YES = Encore LMS for invoice loans; NO = internal PLP loan account only';
COMMENT ON COLUMN plp_program.programs.encore_product_code IS 'Encore product code when lms_entry_in=YES';
