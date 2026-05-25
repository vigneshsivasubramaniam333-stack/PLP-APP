-- V11__programs_description_config.sql
-- Program description + configurable eligibility JSON (merged client-side per PUT semantics).

ALTER TABLE programs ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS config JSONB;
