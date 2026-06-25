-- Replace anchor+invoice_number uniqueness with borrower+number+dates composite.
-- If migration fails on duplicates, resolve rows sharing (borrower_id, invoice_number, invoice_date, due_date) first.

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_number_anchor_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_borrower_number_dates
    ON invoices (borrower_id, invoice_number, invoice_date, due_date);
