-- V6__add_invoice_flow_type.sql
-- Invoice discounting flow: PURCHASE_BILL_DISCOUNTING | SALES_BILL_DISCOUNTING

ALTER TABLE invoices ADD COLUMN flow_type VARCHAR(40) NULL;
