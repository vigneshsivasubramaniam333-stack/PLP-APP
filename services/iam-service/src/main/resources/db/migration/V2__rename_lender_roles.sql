-- Normalize lender role names (legacy → current).
UPDATE users SET role = 'CREDIT_MANAGER' WHERE role = 'PROGRAM_MANAGER';
UPDATE users SET role = 'ACCOUNTS_OFFICER' WHERE role = 'TREASURY';
