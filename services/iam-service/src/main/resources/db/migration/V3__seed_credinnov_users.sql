-- Credinnov sandbox IAM users (password for all: Bltest@123).
-- BCrypt: $2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa

UPDATE users SET status = 'INACTIVE'
WHERE email IN ('admin@plp.com', 'anchor@testcorp.com', 'raj@testcorp.com', 'priya@buyerco.com');

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'admin@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Admin', 'PLATFORM_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('admin@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditmanager@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Manager', 'CREDIT_MANAGER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditmanager@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditofficer@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Officer', 'CREDIT_ANALYST', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditofficer@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditofficer2@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Officer 2', 'CREDIT_ANALYST', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditofficer2@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'sales@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Sales', 'COMPLIANCE_OFFICER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('sales@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'accounts@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Accounts', 'ACCOUNTS_OFFICER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('accounts@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'anchor@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Anchor Admin', 'ANCHOR_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('anchor@credinnov.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'borrower@credinnov.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Borrower', 'BORROWER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('borrower@credinnov.com'));
