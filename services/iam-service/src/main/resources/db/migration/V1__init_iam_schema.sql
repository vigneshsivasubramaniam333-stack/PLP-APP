-- V1__init_iam_schema.sql
-- IAM Service — Users, Roles, Sessions

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(100) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(15),
    role                VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    linked_entity_id    UUID,
    linked_entity_type  VARCHAR(30),
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_linked_entity ON users(linked_entity_id);

-- Seed default users
-- Admin: admin@plp.com / Admin@PLP2026
INSERT INTO users (email, password_hash, full_name, role, status)
VALUES ('admin@plp.com', '$2a$10$N7.761KVsumpiunzy5ckVecEQ3GFMQByFkWhsj3EEN1SMaprSzXPi', 'Platform Admin', 'PLATFORM_ADMIN', 'ACTIVE');

-- Anchor: anchor@testcorp.com / Anchor@2026
INSERT INTO users (email, password_hash, full_name, role, status)
VALUES ('anchor@testcorp.com', '$2a$10$bcoUUI1exNZ.epASqjMUDuq5ofxPW4gKHPKICwC53KLVTqJdPRmyO', 'TestCorp Anchor Admin', 'ANCHOR_ADMIN', 'ACTIVE');

-- Borrower (Employee): raj@testcorp.com / Raj@2026
INSERT INTO users (email, password_hash, full_name, role, status)
VALUES ('raj@testcorp.com', '$2a$10$tMXu3eoj4zbol2i5AlAReOcQ.m9U3LiMgtsi/ArYe.VS.tA14UlGi', 'Raj Kumar', 'BORROWER', 'ACTIVE');

-- Borrower (Buyer): priya@buyerco.com / Priya@2026
INSERT INTO users (email, password_hash, full_name, role, status)
VALUES ('priya@buyerco.com', '$2a$10$RLe.h/ZHIZSpPGfPYdOv8O3.wKuYpnGVuqEdo0Fgpc9ce7D8mhs02', 'Priya Sharma', 'BORROWER', 'ACTIVE');
