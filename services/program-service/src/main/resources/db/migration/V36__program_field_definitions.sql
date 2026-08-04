-- Program custom field definitions catalog (local seed; same system keys as LOS where mapped)

CREATE TABLE IF NOT EXISTS program_field_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_key       VARCHAR(100) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    input_type      VARCHAR(20)  NOT NULL,
    options_json    JSONB,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    product_types   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    system_managed  BOOLEAN      NOT NULL DEFAULT FALSE,
    help_text       VARCHAR(500),
    storage_target  VARCHAR(50)  NOT NULL DEFAULT 'CUSTOM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_program_field_definitions_key UNIQUE (field_key),
    CONSTRAINT chk_program_field_input_type CHECK (input_type IN ('TEXT', 'NUMBER', 'DROPDOWN'))
);

CREATE INDEX IF NOT EXISTS idx_program_field_definitions_active
    ON program_field_definitions (active, sort_order);

-- System-managed eligibility keys using PLP storage names where they already exist
INSERT INTO program_field_definitions (
    field_key, label, input_type, options_json, required, active, sort_order,
    product_types, system_managed, help_text, storage_target
) VALUES
(
    'anchorRelationshipVintageMonths',
    'Min Dir Relationship (months)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    10,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Minimum director / direct relationship vintage in months',
    'CONFIG'
),
(
    'interestPayment',
    'Interest payment',
    'DROPDOWN',
    '[{"value":"UPFRONT","label":"Upfront"},{"value":"MONTHLY","label":"Monthly"},{"value":"REAR_ENDED","label":"Rear ended"}]'::jsonb,
    FALSE,
    TRUE,
    20,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'When interest is collected',
    'CONFIG'
),
(
    'maxInvoiceAgeDays',
    'Max invoice vintage (days)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    30,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maximum allowed invoice age in days (config.maxInvoiceAgeDays)',
    'CONFIG'
),
(
    'maxCmr',
    'Max CMR',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    40,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maximum commercial credit rating (CMR)',
    'CONFIG'
),
(
    'minCibil',
    'Min CIBIL',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    50,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Minimum CIBIL score required',
    'CONFIG'
),
(
    'maxTenureDays',
    'Max tenor (days)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    60,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maps to programs.max_tenure_days',
    'maxTenureDays'
)
ON CONFLICT (field_key) DO NOTHING;
