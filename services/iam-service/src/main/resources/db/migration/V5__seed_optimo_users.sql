-- Optimo sandbox IAM users (password for all: Bltest@123).
-- BCrypt: $2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'admin@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Admin', 'PLATFORM_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('admin@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditmanager@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Manager', 'CREDIT_MANAGER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditmanager@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditofficer@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Officer', 'CREDIT_ANALYST', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditofficer@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'creditofficer2@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Credit Officer 2', 'CREDIT_ANALYST', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('creditofficer2@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'sales@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Sales', 'COMPLIANCE_OFFICER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('sales@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'accounts@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Accounts', 'ACCOUNTS_OFFICER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('accounts@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'anchor@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Anchor Admin', 'ANCHOR_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('anchor@optimo.com'));

INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'borrower@optimo.com', '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'Borrower', 'BORROWER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower('borrower@optimo.com'));
