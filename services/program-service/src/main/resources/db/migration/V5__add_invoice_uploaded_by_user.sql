-- V5__add_invoice_uploaded_by_user.sql
-- Persist IAM user id of invoice uploader (anchor portal maker-checker support)

ALTER TABLE invoices ADD COLUMN uploaded_by_user_id UUID NULL;
