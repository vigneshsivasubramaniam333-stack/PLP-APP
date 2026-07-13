-- L1/L2 maker-checker workflow for programs (approve from program listing).

ALTER TABLE plp_program.programs
    ADD COLUMN IF NOT EXISTS approval_remarks TEXT,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sent_back_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sent_back_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS plp_program.program_approval_config (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    l1_role     VARCHAR(50) NOT NULL DEFAULT 'CREDIT_ANALYST',
    l2_role     VARCHAR(50) NOT NULL DEFAULT 'CREDIT_MANAGER',
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO plp_program.program_approval_config (l1_role, l2_role, enabled)
SELECT 'CREDIT_ANALYST', 'CREDIT_MANAGER', TRUE
WHERE NOT EXISTS (SELECT 1 FROM plp_program.program_approval_config);
