-- V1__init_integration_schema.sql
-- Integration Service — Integration configs, logs, salary/invoice data cache

CREATE TABLE integration_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anchor_id           UUID NOT NULL,
    system_type         VARCHAR(30) NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    base_url            VARCHAR(500),
    credentials         JSONB,
    config              JSONB,
    is_active           BOOLEAN DEFAULT TRUE,
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE salary_data (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         VARCHAR(50) NOT NULL,
    anchor_id           UUID NOT NULL,
    monthly_salary      NUMERIC(15,2),
    net_salary          NUMERIC(15,2),
    earned_salary       NUMERIC(15,2),
    days_worked         INTEGER,
    total_days_in_cycle INTEGER,
    cycle_start_date    DATE,
    cycle_end_date      DATE,
    next_pay_date       DATE,
    fetched_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE invoice_data (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number      VARCHAR(50) NOT NULL,
    anchor_id           UUID NOT NULL,
    buyer_id            VARCHAR(50),
    seller_id           VARCHAR(50),
    invoice_amount      NUMERIC(15,2),
    gst_amount          NUMERIC(15,2),
    net_amount          NUMERIC(15,2),
    invoice_date        DATE,
    due_date            DATE,
    po_number           VARCHAR(50),
    grn_number          VARCHAR(50),
    status              VARCHAR(30),
    credit_days         INTEGER,
    three_way_matched   BOOLEAN DEFAULT FALSE,
    fetched_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE integration_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_id      UUID REFERENCES integration_configs(id),
    request_type        VARCHAR(50),
    request_payload     JSONB,
    response_payload    JSONB,
    http_status         INTEGER,
    response_time_ms    INTEGER,
    is_success          BOOLEAN,
    error_message       VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_integ_configs_anchor ON integration_configs(anchor_id);
CREATE INDEX idx_salary_data_employee ON salary_data(employee_id, anchor_id);
CREATE INDEX idx_invoice_data_number ON invoice_data(invoice_number);
CREATE INDEX idx_invoice_data_buyer ON invoice_data(buyer_id, anchor_id);
CREATE INDEX idx_integ_logs_integration ON integration_logs(integration_id);
CREATE INDEX idx_integ_logs_created ON integration_logs(created_at);
