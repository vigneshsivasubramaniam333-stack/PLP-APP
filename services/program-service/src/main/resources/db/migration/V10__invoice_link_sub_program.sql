-- V10__invoice_link_sub_program.sql
-- Optional link from invoice to sub program (flow / grouping derived from sub program when set)

ALTER TABLE invoices ADD COLUMN sub_program_id UUID NULL REFERENCES sub_programs(id);

CREATE INDEX idx_invoices_sub_program ON invoices(sub_program_id);
