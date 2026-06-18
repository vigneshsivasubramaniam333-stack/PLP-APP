-- Send loan lifecycle notifications via email (same SMTP config pattern as LOS notification-service).

UPDATE plp_notification.notification_templates
SET channel = 'EMAIL'
WHERE template_code IN (
    'LOAN_REQUESTED',
    'LOAN_APPROVED',
    'LOAN_DISBURSED',
    'REPAYMENT_DUE',
    'REPAYMENT_RECEIVED'
);
