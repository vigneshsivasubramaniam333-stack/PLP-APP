-- When true, disbursement/repayment may use program-level borrower_limits without a sub_program_id.
ALTER TABLE loans ADD COLUMN IF NOT EXISTS legacy_program_level_limits BOOLEAN NOT NULL DEFAULT false;
