ALTER TABLE sub_program_borrowers ADD COLUMN IF NOT EXISTS party_code VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sub_program_borrowers_party_code
    ON sub_program_borrowers (sub_program_id, party_code)
    WHERE party_code IS NOT NULL AND party_code <> '';
