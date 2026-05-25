-- V2__seed_report_definitions.sql

INSERT INTO report_definitions (report_code, report_name, category, query_template, parameters) VALUES
('DISBURSEMENT_SUMMARY', 'Daily Disbursement Summary', 'OPERATIONAL', 'Cross-service aggregation via REST', '["fromDate","toDate"]'),
('PORTFOLIO_SUMMARY', 'Portfolio Summary by Program', 'RISK', 'Cross-service aggregation via REST', '[]'),
('OVERDUE_REPORT', 'Overdue/DPD Aging Report', 'RISK', 'Cross-service aggregation via REST', '[]'),
('COLLECTION_SUMMARY', 'Daily Collection/Repayment Summary', 'OPERATIONAL', 'Cross-service aggregation via REST', '["fromDate","toDate"]'),
('PROGRAM_UTILIZATION', 'Program Utilization Dashboard', 'OPERATIONAL', 'Cross-service aggregation via REST', '["programId"]'),
('NPA_REPORT', 'NPA Report (90+ DPD)', 'REGULATORY', 'Cross-service aggregation via REST', '[]');
