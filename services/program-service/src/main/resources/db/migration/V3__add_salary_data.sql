-- V3__add_salary_data.sql
-- Employee salary data for Pay Day Loan eligibility (manual upload or HR integration)

CREATE TABLE employee_salary_data (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id         UUID NOT NULL REFERENCES borrowers(id),
    anchor_id           UUID NOT NULL REFERENCES anchors(id),
    program_id          UUID NOT NULL REFERENCES programs(id),
    employee_code       VARCHAR(30) NOT NULL,
    pay_period          VARCHAR(7) NOT NULL,  -- YYYY-MM format
    gross_salary        NUMERIC(15,2) NOT NULL,
    net_salary          NUMERIC(15,2) NOT NULL,
    days_worked         INTEGER NOT NULL DEFAULT 0,
    total_working_days  INTEGER NOT NULL DEFAULT 30,
    accumulated_salary  NUMERIC(15,2) NOT NULL DEFAULT 0,
    deductions          NUMERIC(15,2) NOT NULL DEFAULT 0,
    eligible_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    eligibility_percent NUMERIC(5,2) NOT NULL DEFAULT 50.00,
    source              VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL, HR_SYSTEM
    verified            BOOLEAN DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    verified_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(borrower_id, pay_period)
);

CREATE INDEX idx_salary_data_borrower ON employee_salary_data(borrower_id);
CREATE INDEX idx_salary_data_anchor ON employee_salary_data(anchor_id);
CREATE INDEX idx_salary_data_program ON employee_salary_data(program_id);
CREATE INDEX idx_salary_data_period ON employee_salary_data(pay_period);
CREATE INDEX idx_salary_data_employee_code ON employee_salary_data(employee_code);
