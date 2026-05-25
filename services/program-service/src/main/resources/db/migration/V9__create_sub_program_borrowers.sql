-- V9__create_sub_program_borrowers.sql
-- Borrower membership and limits per sub program

CREATE TABLE sub_program_borrowers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_program_id      UUID NOT NULL REFERENCES sub_programs(id),
    borrower_id         UUID NOT NULL REFERENCES borrowers(id),
    borrower_limit      NUMERIC(19,2),
    utilized_limit      NUMERIC(19,2) NOT NULL DEFAULT 0,
    available_limit     NUMERIC(19,2),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(sub_program_id, borrower_id)
);

CREATE INDEX idx_sub_program_borrowers_sub ON sub_program_borrowers(sub_program_id);
CREATE INDEX idx_sub_program_borrowers_borrower ON sub_program_borrowers(borrower_id);
