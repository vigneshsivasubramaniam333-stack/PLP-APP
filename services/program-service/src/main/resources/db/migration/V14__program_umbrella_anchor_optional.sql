-- Umbrella programs: no single anchor at program level; anchors are on sub-programs.
ALTER TABLE programs
    ALTER COLUMN anchor_id DROP NOT NULL;

ALTER TABLE programs
    ALTER COLUMN anchor_limit DROP NOT NULL;
