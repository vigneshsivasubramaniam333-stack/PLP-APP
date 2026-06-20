ALTER TABLE plp_iam.users
    ADD COLUMN IF NOT EXISTS password_reset_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN plp_iam.users.password_reset_required IS
    'When true, user must change password on next sign-in (temporary / integration default password).';
