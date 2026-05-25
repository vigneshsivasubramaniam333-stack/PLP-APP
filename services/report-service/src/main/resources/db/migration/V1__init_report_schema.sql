-- V1__init_report_schema.sql
-- Report Service — Read-model tables, report definitions, audit trail

CREATE TABLE report_definitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_code         VARCHAR(50) NOT NULL UNIQUE,
    report_name         VARCHAR(200) NOT NULL,
    category            VARCHAR(50),
    query_template      TEXT,
    parameters          JSONB,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE generated_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_definition_id UUID REFERENCES report_definitions(id),
    requested_by        UUID,
    parameters_used     JSONB,
    file_path           VARCHAR(500),
    format              VARCHAR(10) DEFAULT 'CSV',
    row_count           INTEGER,
    status              VARCHAR(20) DEFAULT 'QUEUED',
    generated_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_trail (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(50) NOT NULL,
    entity_id           UUID NOT NULL,
    action              VARCHAR(50) NOT NULL,
    actor_id            UUID,
    actor_role          VARCHAR(30),
    old_values          JSONB,
    new_values          JSONB,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_trail(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_trail(actor_id);
CREATE INDEX idx_audit_created ON audit_trail(created_at);
CREATE INDEX idx_reports_status ON generated_reports(status);
