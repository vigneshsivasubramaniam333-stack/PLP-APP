-- V2__add_loan_number_sequence.sql
-- Database sequence for globally unique loan number generation (safe for multi-instance)
CREATE SEQUENCE plp_lending.loan_number_seq START WITH 100001 INCREMENT BY 1;
