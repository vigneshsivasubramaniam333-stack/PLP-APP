-- LOS integration: stable borrower identity + idempotent application mapping

ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS source_system VARCHAR(50);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS los_borrower_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS idx_borrowers_source_system_los_borrower_id
    ON borrowers (source_system, los_borrower_id)
    WHERE source_system IS NOT NULL AND los_borrower_id IS NOT NULL;

CREATE TABLE borrower_program_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system       VARCHAR(50) NOT NULL,
    los_application_id  VARCHAR(100) NOT NULL,
    los_borrower_id     VARCHAR(100) NOT NULL,
    borrower_id         UUID NOT NULL REFERENCES borrowers(id),
    program_id          UUID NOT NULL REFERENCES programs(id),
    sub_program_id      UUID NOT NULL REFERENCES sub_programs(id),
    approved_limit      NUMERIC(19, 2) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_to            DATE NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bpm_source_system_los_application UNIQUE (source_system, los_application_id)
);

CREATE INDEX idx_bpm_borrower ON borrower_program_mappings(borrower_id);
CREATE INDEX idx_bpm_program ON borrower_program_mappings(program_id);
CREATE INDEX idx_bpm_sub_program ON borrower_program_mappings(sub_program_id);
