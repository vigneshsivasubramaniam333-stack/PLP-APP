-- Anchor portal onboarding lifecycle (LOS notify → PLP complete)

ALTER TABLE plp_program.anchors
    ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS los_application_id VARCHAR(100);

COMMENT ON COLUMN plp_program.anchors.onboarding_status IS
    'INVITED | IN_PROGRESS | SUBMITTED | SENT_BACK | COMPLETED';
