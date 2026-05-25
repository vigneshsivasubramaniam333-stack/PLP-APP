-- V4__add_salary_data_id_to_loans.sql
-- Audit link from Pay Day Loan to employee_salary_data row used at request time.
-- IF NOT EXISTS: column may already exist from V1__init_lending_schema.sql on fresh installs.

ALTER TABLE loans ADD COLUMN IF NOT EXISTS salary_data_id UUID NULL;
