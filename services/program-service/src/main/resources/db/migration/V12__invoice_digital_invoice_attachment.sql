-- V12__invoice_digital_invoice_attachment.sql
-- Optional original digital invoice file metadata (object storage key when configured).

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS digital_invoice_file_name VARCHAR(255);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS digital_invoice_content_type VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS digital_invoice_storage_key VARCHAR(500);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS digital_invoice_uploaded_at TIMESTAMPTZ;
