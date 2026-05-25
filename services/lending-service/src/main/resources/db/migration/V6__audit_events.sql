CREATE TABLE plp_lending.audit_events (
    id                  UUID PRIMARY KEY,
    event_type          VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(255),
    action              VARCHAR(100) NOT NULL,
    performed_by_user_id VARCHAR(64),
    performed_by_role    VARCHAR(500),
    linked_entity_id     VARCHAR(64),
    linked_entity_type   VARCHAR(64),
    status               VARCHAR(32) NOT NULL,
    message              VARCHAR(2000),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_created_at ON plp_lending.audit_events (created_at DESC);
CREATE INDEX idx_audit_events_entity ON plp_lending.audit_events (entity_type, entity_id);
