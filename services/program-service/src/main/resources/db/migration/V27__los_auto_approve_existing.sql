-- LOS-originated entities are pre-approved upstream; activate any records still pending from earlier syncs.

UPDATE anchors
SET status = 'ACTIVE'
WHERE source_system IS NOT NULL
  AND los_anchor_id IS NOT NULL
  AND status = 'DRAFT';

UPDATE programs
SET status = 'ACTIVE'
WHERE source_system IS NOT NULL
  AND los_program_id IS NOT NULL
  AND status = 'DRAFT';

UPDATE sub_programs
SET status = 'ACTIVE'
WHERE source_system IS NOT NULL
  AND los_sub_program_id IS NOT NULL
  AND status = 'DRAFT';

UPDATE borrowers
SET status = 'ACTIVE'
WHERE source_system IS NOT NULL
  AND los_borrower_id IS NOT NULL
  AND status = 'PENDING_KYC';

UPDATE borrower_program_mappings
SET status = 'APPROVED'
WHERE source_system IS NOT NULL
  AND los_application_id IS NOT NULL
  AND status = 'PENDING_APPROVAL';

UPDATE sub_program_borrowers spb
SET status = 'ACTIVE'
WHERE spb.status = 'PENDING_APPROVAL'
  AND EXISTS (
    SELECT 1
    FROM borrowers b
    WHERE b.id = spb.borrower_id
      AND b.source_system IS NOT NULL
      AND b.los_borrower_id IS NOT NULL
  );
