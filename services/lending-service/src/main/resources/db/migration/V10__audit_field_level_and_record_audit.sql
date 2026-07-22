-- Enrich lending audit_events with field-level snapshots + entity record audit tables.
ALTER TABLE plp_lending.audit_events
    ADD COLUMN IF NOT EXISTS old_values JSONB,
    ADD COLUMN IF NOT EXISTS new_values JSONB,
    ADD COLUMN IF NOT EXISTS changed_fields TEXT;

CREATE TABLE IF NOT EXISTS plp_lending.entity_record_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(255) NOT NULL,
    action              VARCHAR(40) NOT NULL,
    status_at_change    VARCHAR(64),
    performed_by        VARCHAR(64),
    performed_by_role   VARCHAR(500),
    old_row             JSONB,
    new_row             JSONB,
    changed_fields      TEXT,
    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lending_entity_record_audit_entity
    ON plp_lending.entity_record_audit (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lending_entity_record_audit_created
    ON plp_lending.entity_record_audit (created_at DESC);
