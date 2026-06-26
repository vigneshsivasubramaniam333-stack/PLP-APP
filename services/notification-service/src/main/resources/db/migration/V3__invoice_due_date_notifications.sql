-- Invoice due-date reminder template and admin event toggles.

INSERT INTO plp_notification.notification_templates (template_code, channel, subject, body_template, variables)
VALUES (
    'INVOICE_DUE_DATE_REMINDER',
    'EMAIL',
    'Invoice Due Date Reminder',
    'Dear {{borrowerName}},

This is an auto reminder for your invoice.

Invoice #: {{invoiceNumber}}
Vendor: {{vendorName}}
Due Date: {{dueDate}}
Amount Due: {{amountDue}}

To avoid any late fees or penalties, kindly ensure the payment is completed before the due date.

If payment has already been made, please disregard this message.

Make Payment: {{paymentUrl}}

BillionTech | Need Help? Contact Support',
    '["borrowerName","invoiceNumber","vendorName","dueDate","amountDue","paymentUrl"]'
)
ON CONFLICT (template_code) DO UPDATE SET
    channel = EXCLUDED.channel,
    subject = EXCLUDED.subject,
    body_template = EXCLUDED.body_template,
    variables = EXCLUDED.variables,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS plp_notification.notification_event_settings (
    event_code   VARCHAR(50) PRIMARY KEY,
    description  VARCHAR(200) NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO plp_notification.notification_event_settings (event_code, description, enabled) VALUES
    ('LOAN_REQUESTED', 'Email when a loan request is submitted', TRUE),
    ('LOAN_APPROVED', 'Email when a loan is sanctioned', TRUE),
    ('LOAN_DISBURSED', 'Email when a loan is disbursed', TRUE),
    ('REPAYMENT_DUE', 'Email when repayment is due', TRUE),
    ('REPAYMENT_RECEIVED', 'Email when repayment is received', TRUE),
    ('INVOICE_DISBURSED_NOTIFICATION', 'Email borrower when an invoice discounting loan is disbursed', TRUE),
    ('INVOICE_DUE_DATE_REMINDER_DAILY', 'Daily email reminder for open invoice discounting loans until repaid', TRUE)
ON CONFLICT (event_code) DO NOTHING;
