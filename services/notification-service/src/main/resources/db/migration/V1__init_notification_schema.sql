-- V1__init_notification_schema.sql
-- Notification Service — Templates, Sent Notifications, Preferences

CREATE TABLE notification_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code       VARCHAR(50) NOT NULL UNIQUE,
    channel             VARCHAR(20) NOT NULL,
    subject             VARCHAR(200),
    body_template       TEXT NOT NULL,
    variables           JSONB,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id         UUID REFERENCES notification_templates(id),
    recipient_id        UUID NOT NULL,
    recipient_email     VARCHAR(100),
    recipient_phone     VARCHAR(15),
    channel             VARCHAR(20) NOT NULL,
    subject             VARCHAR(200),
    body                TEXT,
    reference_type      VARCHAR(50),
    reference_id        UUID,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    failure_reason      VARCHAR(500),
    retry_count         INTEGER DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_reference ON notifications(reference_type, reference_id);

-- Seed default templates
INSERT INTO notification_templates (template_code, channel, subject, body_template, variables) VALUES
('LOAN_REQUESTED', 'IN_APP', 'Loan Request Received - {{loanNumber}}', 'Dear {{borrowerName}}, Your loan request {{loanNumber}} for ₹{{amount}} has been received and is being processed.', '["borrowerName","loanNumber","amount"]'),
('LOAN_APPROVED', 'IN_APP', 'Loan Approved - {{loanNumber}}', 'Dear {{borrowerName}}, Your loan {{loanNumber}} for ₹{{amount}} has been approved. Disbursement will be initiated shortly.', '["borrowerName","loanNumber","amount"]'),
('LOAN_DISBURSED', 'IN_APP', 'Disbursement Completed - {{loanNumber}}', 'Dear {{borrowerName}}, ₹{{amount}} has been credited to your account for loan {{loanNumber}}. Due date: {{dueDate}}.', '["borrowerName","loanNumber","amount","dueDate"]'),
('REPAYMENT_DUE', 'IN_APP', 'Repayment Due - {{loanNumber}}', 'Dear {{borrowerName}}, Repayment of ₹{{amount}} is due on {{dueDate}} for loan {{loanNumber}}.', '["borrowerName","loanNumber","amount","dueDate"]'),
('REPAYMENT_RECEIVED', 'IN_APP', 'Payment Received - {{loanNumber}}', 'Dear {{borrowerName}}, We have received ₹{{amount}} against loan {{loanNumber}}. Outstanding: ₹{{outstanding}}.', '["borrowerName","loanNumber","amount","outstanding"]');
