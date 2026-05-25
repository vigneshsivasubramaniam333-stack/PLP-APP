-- V7__add_invoice_borrower_acceptance.sql
-- Purchase bill discounting: borrower accepts anchor-eligible invoice before financing

ALTER TABLE invoices ADD COLUMN borrower_accepted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN borrower_accepted_at TIMESTAMPTZ NULL;
