-- V3__loan_status_approved_to_sanctioned.sql
-- Terminology: credit-approved loans are stored as SANCTIONED (replaces legacy APPROVED).

UPDATE loans SET status = 'SANCTIONED' WHERE status = 'APPROVED';
