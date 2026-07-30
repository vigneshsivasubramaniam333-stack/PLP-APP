-- APPROVED_PENDING_DOCS (21 chars) exceeds programs.status VARCHAR(20).
ALTER TABLE plp_program.programs
    ALTER COLUMN status TYPE VARCHAR(40);
