CREATE TABLE los_sync_audit (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_type    VARCHAR(40) NOT NULL,
    resource_id      UUID,
    source_system    VARCHAR(50) NOT NULL,
    external_id      VARCHAR(200) NOT NULL,
    request_payload  JSONB,
    response_payload JSONB,
    status           VARCHAR(20) NOT NULL,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_los_sync_audit_resource ON los_sync_audit (resource_type, resource_id);
CREATE INDEX idx_los_sync_audit_created ON los_sync_audit (created_at DESC);
CREATE INDEX idx_los_sync_audit_external ON los_sync_audit (source_system, external_id);
