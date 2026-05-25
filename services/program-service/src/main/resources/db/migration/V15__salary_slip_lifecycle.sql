-- Salary slip lifecycle (number, external ref, period bounds, persisted status)

CREATE SEQUENCE IF NOT EXISTS plp_program.salary_slip_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE plp_program.employee_salary_data
    ADD COLUMN IF NOT EXISTS salary_slip_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS external_reference_number VARCHAR(128),
    ADD COLUMN IF NOT EXISTS period_from DATE,
    ADD COLUMN IF NOT EXISTS period_to DATE,
    ADD COLUMN IF NOT EXISTS slip_status VARCHAR(40) NOT NULL DEFAULT 'AVAILABLE_FOR_LOAN';

-- Backfill period bounds from pay_period only when pay_period is YYYY-MM (avoids to_date failure on malformed values)
UPDATE plp_program.employee_salary_data e
SET period_from = to_date(e.pay_period || '-01', 'YYYY-MM-DD'),
    period_to   = (date_trunc('month', to_date(e.pay_period || '-01', 'YYYY-MM-DD')) + interval '1 month - 1 day')::date
WHERE (e.period_from IS NULL OR e.period_to IS NULL)
  AND e.pay_period ~ '^\d{4}-\d{2}$';

-- Remaining rows with missing period (non-YYYY-MM pay_period): use created month so NOT NULL constraints can be applied
UPDATE plp_program.employee_salary_data e
SET period_from = date_trunc('month', e.created_at)::date,
    period_to   = (date_trunc('month', e.created_at) + interval '1 month - 1 day')::date
WHERE (e.period_from IS NULL OR e.period_to IS NULL);

UPDATE plp_program.employee_salary_data e
SET salary_slip_number = 'SSL-' || lpad(nextval('plp_program.salary_slip_number_seq')::text, 10, '0')
WHERE e.salary_slip_number IS NULL;

ALTER TABLE plp_program.employee_salary_data
    ALTER COLUMN salary_slip_number SET NOT NULL,
    ALTER COLUMN period_from SET NOT NULL,
    ALTER COLUMN period_to SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_salary_data_slip_number
    ON plp_program.employee_salary_data (salary_slip_number);

CREATE INDEX IF NOT EXISTS idx_salary_data_period_range
    ON plp_program.employee_salary_data (borrower_id, period_from, period_to);
