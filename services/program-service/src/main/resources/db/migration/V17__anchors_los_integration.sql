-- LOS anchor sync: external identity + idempotent upsert by source_system + los_anchor_id

ALTER TABLE anchors ADD COLUMN IF NOT EXISTS source_system VARCHAR(50);
ALTER TABLE anchors ADD COLUMN IF NOT EXISTS los_anchor_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_anchors_source_system_los_anchor_id
    ON anchors (source_system, los_anchor_id)
    WHERE source_system IS NOT NULL AND los_anchor_id IS NOT NULL;
