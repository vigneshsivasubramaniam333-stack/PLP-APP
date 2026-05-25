-- V8__create_sub_programs.sql
-- Sub programs: operating arrangement under a program (anchor, lender, flow, limits)

CREATE TABLE sub_programs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id          UUID NOT NULL REFERENCES programs(id),
    anchor_id           UUID NOT NULL REFERENCES anchors(id),
    lender_id           UUID,
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    flow_type           VARCHAR(40) NOT NULL,
    anchor_role         VARCHAR(20) NOT NULL,
    borrower_role       VARCHAR(20) NOT NULL,
    sub_program_limit   NUMERIC(19,2),
    utilized_limit      NUMERIC(19,2) NOT NULL DEFAULT 0,
    available_limit     NUMERIC(19,2),
    interest_rate       NUMERIC(8,4),
    margin_percent      NUMERIC(8,4),
    max_tenure_days     INTEGER,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sub_programs_program ON sub_programs(program_id);
CREATE INDEX idx_sub_programs_anchor ON sub_programs(anchor_id);
CREATE INDEX idx_sub_programs_status ON sub_programs(status);
