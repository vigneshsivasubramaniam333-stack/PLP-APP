-- LOS integration: external program / sub-program identifiers

ALTER TABLE programs ADD COLUMN IF NOT EXISTS source_system VARCHAR(50);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS los_program_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_programs_source_system_los_program_id
    ON programs (source_system, los_program_id)
    WHERE source_system IS NOT NULL AND los_program_id IS NOT NULL;

ALTER TABLE sub_programs ADD COLUMN IF NOT EXISTS source_system VARCHAR(50);
ALTER TABLE sub_programs ADD COLUMN IF NOT EXISTS los_sub_program_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sub_programs_source_system_los_sub_program_id
    ON sub_programs (source_system, los_sub_program_id)
    WHERE source_system IS NOT NULL AND los_sub_program_id IS NOT NULL;
