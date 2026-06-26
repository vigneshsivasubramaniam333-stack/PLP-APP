-- Per-borrower repayment / collection account metadata (sub-program enrollment level).

ALTER TABLE sub_program_borrowers
    ADD COLUMN IF NOT EXISTS borrower_od_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS borrower_od_bank_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS borrower_od_bank_ifsc VARCHAR(20),
    ADD COLUMN IF NOT EXISTS borrower_od_account_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS idfc_collection_account_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS idfc_od_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS idfc_ifsc_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS idfc_upi_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS castler_escrow_account_in VARCHAR(30),
    ADD COLUMN IF NOT EXISTS castler_escrow_account_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS castler_escrow_payee_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS razorpay_route_account_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS razorpay_smart_collect_ac_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS razorpay_fee DECIMAL(19, 4),
    ADD COLUMN IF NOT EXISTS hdfc_account_no VARCHAR(50),
    ADD COLUMN IF NOT EXISTS hdfc_ifsc_code VARCHAR(20);
