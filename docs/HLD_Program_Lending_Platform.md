# High-Level Design (HLD)

## Program Lending Platform — Pay Day Loan & Invoice Discounting

**Version:** 1.0  
**Date:** May 2026  
**Author:** BillionTech Engineering  
**Status:** Draft for Review  

---

## Table of Contents

1. [Design Goals & Principles](#1-design-goals--principles)
2. [System Architecture Overview](#2-system-architecture-overview)
3. [Technology Stack](#3-technology-stack)
4. [Service Decomposition](#4-service-decomposition)
5. [Data Architecture](#5-data-architecture)
6. [API Design](#6-api-design)
7. [Core Workflows](#7-core-workflows)
8. [Integration Architecture](#8-integration-architecture)
9. [Security Architecture](#9-security-architecture)
10. [Frontend Architecture](#10-frontend-architecture)
11. [Deployment Architecture](#11-deployment-architecture)
12. [Monitoring & Observability](#12-monitoring--observability)
13. [Scalability & Performance](#13-scalability--performance)
14. [Disaster Recovery & High Availability](#14-disaster-recovery--high-availability)
15. [Migration & Rollout Strategy](#15-migration--rollout-strategy)

---

## 1. Design Goals & Principles

### 1.1 Design Goals

| Goal | Description |
|------|-------------|
| **Independence** | Standalone application — no hard dependency on BillionTech LoS for core operations |
| **API-First** | All functionality exposed via well-documented REST APIs |
| **Product Agnostic Core** | Shared program/limit engine serves both Pay Day and Invoice Discounting (and future products) |
| **High Volume** | Designed for 50,000+ daily transactions with sub-second eligibility checks |
| **Regulatory Compliance** | Built-in compliance guardrails (KFS, cooling-off, data residency, audit trail) |
| **Configurability** | Business rules, parameters, and workflows configurable without code changes |
| **Tech Stack Alignment** | Same technology choices as BillionTech LoS for shared expertise and operational efficiency |

### 1.2 Design Principles

1. **Microservices Architecture** — Independent, deployable services with clear bounded contexts
2. **Event-Driven Processing** — Asynchronous operations via message queues for non-blocking workflows
3. **Domain-Driven Design (DDD)** — Clear domain boundaries: Program, Lending, Integration, Administration
4. **CQRS for High-Read Scenarios** — Separate read models for dashboards and reporting
5. **Idempotent Operations** — All financial operations (disbursement, repayment) are idempotent
6. **Fail-Safe Defaults** — On integration failure, deny loan (not approve); freeze limits (not open)
7. **Audit Everything** — Every state change recorded with actor, timestamp, previous state, new state
8. **Configuration over Code** — Business rules, interest rates, limits expressed as configuration
9. **API Versioning** — All APIs versioned (v1, v2) for backward compatibility
10. **Horizontal Scalability** — Stateless services; state in PostgreSQL/Redis; scale by adding instances

---

## 2. System Architecture Overview

### 2.1 High-Level Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                               │
│                                                                                    │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │ Platform UI │  │ Anchor Portal│  │ Borrower Portal  │  │ External Clients │   │
│  │ (React SPA) │  │ (React SPA)  │  │ (React SPA)      │  │ (API Consumers)  │   │
│  │   :3000     │  │   :3001      │  │   :3002           │  │                  │   │
│  └──────┬──────┘  └──────┬───────┘  └────────┬──────────┘  └────────┬─────────┘   │
│         │                │                    │                       │              │
└─────────┼────────────────┼────────────────────┼───────────────────────┼──────────────┘
          │                │                    │                       │
          ▼                ▼                    ▼                       ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY (:8080)                                        │
│  Spring Cloud Gateway — JWT validation, Rate limiting, CORS, Routing, OWASP       │
└────────────────────────────────┬──────────────────────────────────────────────────┘
                                 │
                    ┌────────────┼────────────────────────────────┐
                    │            │                                │
                    ▼            ▼                                ▼
┌────────────────────┐ ┌─────────────────────┐  ┌──────────────────────────────────┐
│  IAM Service       │ │  Program Service     │  │  Lending Service                  │
│  (:8081)           │ │  (:8082)             │  │  (:8083)                          │
│                    │ │                      │  │                                    │
│  • Authentication  │ │  • Program CRUD      │  │  • Loan Request Processing        │
│  • Authorization   │ │  • Anchor Mgmt       │  │  • Eligibility Engine             │
│  • User Mgmt      │ │  • Borrower Mgmt     │  │  • Disbursement Orchestration     │
│  • RBAC           │ │  • Limit Engine       │  │  • Repayment Processing           │
│  • API Keys       │ │  • KYC Orchestration  │  │  • Interest/Fee Calculation       │
│  • Sessions       │ │  • Parameter Config   │  │  • KFS Generation                 │
│                    │ │  • Eligibility Rules  │  │  • Overdue Management             │
└────────────────────┘ └─────────────────────┘  └──────────────────────────────────┘
                                 │                                │
                    ┌────────────┼────────────────────────────────┤
                    │            │                                │
                    ▼            ▼                                ▼
┌────────────────────┐ ┌─────────────────────┐  ┌──────────────────────────────────┐
│  Integration Svc   │ │  Notification Svc    │  │  Report Service                   │
│  (:8084)           │ │  (:8085)             │  │  (:8086)                          │
│                    │ │                      │  │                                    │
│  • HR System Conn  │ │  • SMS/Email/WA      │  │  • Operational Reports            │
│  • ERP System Conn │ │  • Template Engine   │  │  • MIS & Analytics                │
│  • Payment Gateway │ │  • Delivery Tracking │  │  • Regulatory Reporting           │
│  • NACH/e-NACH    │ │  • Event Triggers    │  │  • Export (CSV/PDF/Excel)         │
│  • e-Sign         │ │  • Notification Log  │  │  • Scheduled Reports              │
│  • Bank Verify    │ │                      │  │  • Bureau File Generation         │
│  • GST Verify     │ │                      │  │                                    │
│  • LoS Bridge     │ │                      │  │                                    │
└────────────────────┘ └─────────────────────┘  └──────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         SERVICE REGISTRY — Eureka (:8761)                          │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                                      │
│                                                                                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐                 │
│  │PostgreSQL  │  │  Redis 7.2 │  │ RabbitMQ   │  │   MinIO    │                 │
│  │   16       │  │            │  │   3.13     │  │            │                 │
│  │            │  │ • Caching  │  │            │  │ • Document │                 │
│  │ • Programs │  │ • Limits   │  │ • Events   │  │   Storage  │                 │
│  │ • Loans    │  │ • Sessions │  │ • Notifs   │  │ • Invoices │                 │
│  │ • Anchors  │  │ • Rate     │  │ • Async    │  │ • KYC Docs │                 │
│  │ • Borrowers│  │   Limiting │  │   Tasks    │  │ • KFS/Agmt │                 │
│  │ • Txns     │  │ • Idempot. │  │            │  │            │                 │
│  │ • Audit    │  │   Keys     │  │            │  │            │                 │
│  └────────────┘  └────────────┘  └────────────┘  └────────────┘                 │
│       :5432           :6379        :5672/:15672     :9000/:9001                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 External System Integration Map

```
┌─────────────────────────────────────────────────────────────┐
│                   EXTERNAL SYSTEMS                            │
│                                                              │
│  ┌────────────────┐    ┌─────────────────┐                  │
│  │  HR Systems    │    │  ERP Systems     │                  │
│  │  • SAP SF      │    │  • SAP           │                  │
│  │  • Darwinbox   │    │  • Oracle        │                  │
│  │  • greytHR     │    │  • Tally         │                  │
│  │  • Keka        │    │  • Zoho Books    │                  │
│  └───────┬────────┘    └────────┬─────────┘                  │
│          │                      │                            │
│  ┌───────┴──────────────────────┴─────────┐                  │
│  │         Integration Service             │                  │
│  │    (Adapter Pattern per system)         │                  │
│  └───────┬──────────────────────┬─────────┘                  │
│          │                      │                            │
│  ┌───────┴────────┐    ┌───────┴─────────┐                  │
│  │ Payment Infra  │    │ BillionTech LoS  │                  │
│  │ • Razorpay     │    │ • KYC Service    │                  │
│  │ • Cashfree     │    │ • Bureau Pull    │                  │
│  │ • NACH (NPCI)  │    │ • Notifications  │                  │
│  │ • IMPS/NEFT    │    │ • LMS Handover   │                  │
│  └────────────────┘    └─────────────────┘                  │
│                                                              │
│  ┌────────────────┐    ┌─────────────────┐                  │
│  │ Verification   │    │ Document/Sign   │                  │
│  │ • NSDL (PAN)   │    │ • Leegality     │                  │
│  │ • UIDAI (Aad.) │    │ • eMsigner      │                  │
│  │ • GST (NIC)    │    │ • DigiLocker     │                  │
│  └────────────────┘    └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

### 3.1 Backend

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Runtime | Java | 21 (LTS) | Virtual Threads for high concurrency |
| Framework | Spring Boot | 3.4 | Microservice foundation |
| Cloud | Spring Cloud | 2024.0 | Service discovery, gateway, config |
| API Gateway | Spring Cloud Gateway | Reactive | Routing, JWT, rate limiting |
| Service Discovery | Netflix Eureka | — | Service registry |
| Database | PostgreSQL | 16 | Primary data store |
| Cache | Redis | 7.2 | Caching, rate limiting, distributed locks |
| Message Queue | RabbitMQ | 3.13 | Event-driven async processing |
| Object Storage | MinIO | Latest | Documents, invoices, agreements |
| ORM | Hibernate/JPA | 6.4 | Object-relational mapping |
| Migration | Flyway | 10.x | Database schema versioning |
| API Docs | SpringDoc OpenAPI | 3.1 | Swagger UI + spec generation |
| Build | Maven | 3.9+ | Multi-module build |

### 3.2 Frontend

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Framework | React | 18/19 | SPA framework |
| Language | TypeScript | 5.x | Type safety |
| Styling | Tailwind CSS | 4.x | Utility-first CSS |
| State Management | React Query / Zustand | Latest | Server state + client state |
| HTTP Client | Axios | Latest | API communication |
| Routing | React Router | 6.x | Client-side routing |
| Forms | React Hook Form + Zod | Latest | Form management + validation |
| Charts | Recharts / Chart.js | Latest | Dashboard visualizations |
| Tables | TanStack Table | Latest | Data grids with sorting/filtering |
| Build | Vite | Latest | Fast build tooling |

### 3.3 Infrastructure & DevOps

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Containerization | Docker | Service packaging |
| Orchestration | Docker Compose / Kubernetes | Local dev / Production |
| CI/CD | GitHub Actions | Build, test, deploy |
| Monitoring | Prometheus + Grafana | Metrics and dashboards |
| Logging | ELK Stack (or Loki) | Centralized logging |
| Tracing | Micrometer + Zipkin | Distributed tracing |
| Secret Mgmt | HashiCorp Vault / AWS Secrets | Production secrets |

---

## 4. Service Decomposition

### 4.1 Service Catalog

| Service | Port | Bounded Context | Key Responsibilities |
|---------|------|-----------------|---------------------|
| **api-gateway** | 8080 | Cross-cutting | Request routing, JWT validation, rate limiting, CORS, OWASP headers |
| **iam-service** | 8081 | Identity & Access | Authentication, authorization, RBAC, user management, API keys |
| **program-service** | 8082 | Program Management | Programs, Anchors, Borrowers, Limits, Eligibility, KYC orchestration |
| **lending-service** | 8083 | Lending Operations | Loan requests, approval engine, disbursement, repayment, interest calc |
| **integration-service** | 8084 | External Integration | HR/ERP adapters, payment gateway, NACH, e-Sign, verification services |
| **notification-service** | 8085 | Communication | SMS, Email, WhatsApp, templates, delivery tracking |
| **report-service** | 8086 | Reporting & Analytics | Operational reports, MIS, regulatory files, scheduled reports |
| **discovery-service** | 8761 | Infrastructure | Service registration and discovery |

### 4.2 Service Dependencies

```
api-gateway ──→ [all services via routing]
iam-service ──→ [standalone — Redis for sessions]
program-service ──→ integration-service (KYC verification)
                 ──→ notification-service (onboarding notifications)
                 ──→ iam-service (user creation for anchors/borrowers)
lending-service ──→ program-service (limit checks, eligibility)
                ──→ integration-service (disbursement, repayment)
                ──→ notification-service (loan lifecycle notifications)
                ──→ report-service (transaction reporting)
integration-service ──→ [External: HR, ERP, Payment GW, e-Sign, Verification]
                    ──→ [External: BillionTech LoS APIs]
report-service ──→ [reads from program-service DB, lending-service DB via views/APIs]
notification-service ──→ [External: SMS/Email/WhatsApp providers]
```

### 4.3 Service Communication

| Pattern | Use Case | Technology |
|---------|----------|-----------|
| **Synchronous (REST)** | Real-time operations: eligibility check, limit validation, disbursement trigger | REST over HTTP/2, JWT auth |
| **Asynchronous (Events)** | Non-blocking operations: notifications, reporting, audit logging, data sync | RabbitMQ with durable queues |
| **Scheduled (Cron)** | Periodic tasks: salary data sync, limit refresh, report generation, overdue marking | Spring Scheduler + distributed lock (Redis) |

### 4.4 Inter-Service Event Bus (RabbitMQ)

| Exchange | Queue | Publisher | Consumer | Event Type |
|----------|-------|-----------|----------|-----------|
| `program.events` | `program.created.q` | program-service | report-service, notification-service | Program lifecycle |
| `program.events` | `borrower.onboarded.q` | program-service | lending-service, notification-service | Borrower activated |
| `lending.events` | `loan.disbursed.q` | lending-service | report-service, notification-service, integration-service | Loan disbursed |
| `lending.events` | `loan.repaid.q` | lending-service | program-service (limit update), report-service | Repayment received |
| `lending.events` | `loan.overdue.q` | lending-service | notification-service, program-service (freeze) | Loan overdue |
| `integration.events` | `salary.synced.q` | integration-service | program-service (limit recalc) | HR data refreshed |
| `integration.events` | `invoice.received.q` | integration-service | lending-service (new eligible invoice) | Invoice from ERP |
| `integration.events` | `payment.completed.q` | integration-service | lending-service (mark disbursed/repaid) | Payment confirmation |

---

## 5. Data Architecture

### 5.1 Database Strategy

- **Schema-per-service** isolation within a single PostgreSQL instance (same pattern as BillionTech LoS)
- Each service owns its schema; no cross-schema joins
- Read replicas for report-service (CQRS pattern)
- JSONB columns for flexible/evolving attributes (program parameters, integration configs)
- UUID primary keys for all entities

| Schema | Owning Service | Key Tables |
|--------|---------------|-----------|
| `plp_iam` | iam-service | users, roles, permissions, api_keys, sessions |
| `plp_program` | program-service | programs, anchors, borrowers, borrower_limits, eligibility_rules, kyc_results |
| `plp_lending` | lending-service | loan_requests, loans, disbursements, repayments, interest_accruals, kfs_documents |
| `plp_integration` | integration-service | integration_configs, hr_salary_data, erp_invoices, payment_transactions, webhook_events |
| `plp_notification` | notification-service | notification_logs, templates, delivery_status |
| `plp_report` | report-service | report_configs, generated_reports, regulatory_files |

### 5.2 Core Entity Model

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                PROGRAM SCHEMA (plp_program)                               │
│                                                                                          │
│  ┌──────────────────┐        ┌────────────────────┐        ┌───────────────────────┐    │
│  │     programs     │        │      anchors       │        │      borrowers        │    │
│  ├──────────────────┤        ├────────────────────┤        ├───────────────────────┤    │
│  │ id (UUID)        │◄──┐   │ id (UUID)          │◄──┐   │ id (UUID)             │    │
│  │ program_code     │   │   │ anchor_code        │   │   │ borrower_code         │    │
│  │ product_type     │   │   │ entity_name        │   │   │ name                  │    │
│  │ anchor_id ────────┼───┼──►│ entity_type        │   │   │ program_id ────────────┼──► │
│  │ lender_id        │   │   │ gstin              │   │   │ anchor_id ─────────────┼──► │
│  │ program_limit    │   │   │ pan                │   │   │ kyc_status            │    │
│  │ anchor_limit     │   │   │ cin                │   │   │ status                │    │
│  │ interest_rate_min│   │   │ address (JSONB)    │   │   │ personal_info (JSONB) │    │
│  │ interest_rate_max│   │   │ bank_account (JSONB)│   │   │ bank_account (JSONB)  │    │
│  │ max_tenure_days  │   │   │ integration_config │   │   │ employment_info (JSONB)│   │
│  │ margin_percent   │   │   │ agreement_doc_id   │   │   │ created_at            │    │
│  │ parameters (JSONB│   │   │ status             │   │   │ updated_at            │    │
│  │ eligibility_rules│   │   │ rating             │   │   └───────────────────────┘    │
│  │ (JSONB)         │   │   │ created_at         │   │                                 │
│  │ status           │   │   │ updated_at         │   │   ┌───────────────────────┐    │
│  │ valid_from       │   │   └────────────────────┘   │   │   borrower_limits     │    │
│  │ valid_to         │   │                            │   ├───────────────────────┤    │
│  │ created_at       │   │                            │   │ id (UUID)             │    │
│  │ updated_at       │   │                            └───│ borrower_id           │    │
│  └──────────────────┘   │                                │ program_id            │    │
│                          │                                │ sanctioned_limit      │    │
│                          │                                │ utilized_limit        │    │
│                          │                                │ available_limit       │    │
│                          │                                │ overdue_amount        │    │
│                          │                                │ interest_rate         │    │
│                          │                                │ max_concurrent_loans  │    │
│                          │                                │ status                │    │
│                          │                                │ last_evaluated_at     │    │
│                          │                                │ created_at            │    │
│                          │                                │ updated_at            │    │
│                          │                                └───────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                LENDING SCHEMA (plp_lending)                               │
│                                                                                          │
│  ┌──────────────────────┐      ┌─────────────────────┐     ┌──────────────────────┐    │
│  │    loan_requests     │      │       loans         │     │    disbursements     │    │
│  ├──────────────────────┤      ├─────────────────────┤     ├──────────────────────┤    │
│  │ id (UUID)            │      │ id (UUID)           │     │ id (UUID)            │    │
│  │ request_number       │      │ loan_number         │     │ loan_id              │    │
│  │ borrower_id          │      │ request_id          │     │ amount               │    │
│  │ program_id           │      │ borrower_id         │     │ mode (IMPS/NEFT/UPI) │    │
│  │ product_type         │      │ program_id          │     │ utr_number           │    │
│  │ requested_amount     │      │ principal_amount    │     │ bank_ref             │    │
│  │ eligible_amount      │      │ interest_rate       │     │ status               │    │
│  │ purpose              │      │ tenure_days         │     │ beneficiary_account  │    │
│  │ source_data (JSONB)  │      │ disbursement_date   │     │ initiated_at         │    │
│  │  • salary_earned     │      │ due_date            │     │ completed_at         │    │
│  │  • invoice_id        │      │ interest_amount     │     │ failure_reason       │    │
│  │  • invoice_number    │      │ fee_amount          │     └──────────────────────┘    │
│  │ status               │      │ total_repayable     │                                  │
│  │ auto_approved        │      │ amount_repaid       │     ┌──────────────────────┐    │
│  │ reviewed_by          │      │ outstanding_amount  │     │     repayments       │    │
│  │ review_remarks       │      │ overdue_amount      │     ├──────────────────────┤    │
│  │ kfs_doc_id           │      │ penal_interest      │     │ id (UUID)            │    │
│  │ consent_given_at     │      │ dpd                 │     │ loan_id              │    │
│  │ cooling_off_expiry   │      │ status              │     │ amount               │    │
│  │ created_at           │      │ closed_at           │     │ principal_component  │    │
│  │ updated_at           │      │ created_at          │     │ interest_component   │    │
│  └──────────────────────┘      │ updated_at          │     │ penal_component      │    │
│                                 └─────────────────────┘     │ mode                 │    │
│                                                              │ reference_number    │    │
│  ┌──────────────────────┐                                    │ source (SALARY_DED/ │    │
│  │   kfs_documents      │                                    │  NACH/MANUAL/UPI)   │    │
│  ├──────────────────────┤                                    │ status              │    │
│  │ id (UUID)            │                                    │ received_at         │    │
│  │ loan_request_id      │                                    └──────────────────────┘    │
│  │ apr                  │                                                                │
│  │ total_interest       │     ┌──────────────────────────┐                              │
│  │ total_fees           │     │    interest_accruals     │                              │
│  │ total_repayable      │     ├──────────────────────────┤                              │
│  │ repayment_schedule   │     │ id (UUID)                │                              │
│  │ (JSONB)             │     │ loan_id                  │                              │
│  │ generated_at         │     │ accrual_date             │                              │
│  │ borrower_consent_at  │     │ principal_outstanding    │                              │
│  │ document_url         │     │ interest_amount          │                              │
│  │ cooling_off_availed  │     │ penal_amount             │                              │
│  └──────────────────────┘     │ cumulative_interest      │                              │
│                                └──────────────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                            INTEGRATION SCHEMA (plp_integration)                           │
│                                                                                          │
│  ┌─────────────────────┐     ┌──────────────────────┐     ┌──────────────────────┐     │
│  │  integration_configs│     │   salary_records     │     │     invoices         │     │
│  ├─────────────────────┤     ├──────────────────────┤     ├──────────────────────┤     │
│  │ id (UUID)           │     │ id (UUID)            │     │ id (UUID)            │     │
│  │ anchor_id           │     │ borrower_id          │     │ anchor_id (seller)   │     │
│  │ system_type         │     │ anchor_id (employer) │     │ borrower_id (buyer)  │     │
│  │ (HR/ERP/PAYMENT)    │     │ pay_period_start     │     │ invoice_number       │     │
│  │ provider            │     │ pay_period_end       │     │ invoice_date         │     │
│  │ (SAP/DARWINBOX/etc) │     │ gross_salary         │     │ invoice_amount       │     │
│  │ config (JSONB)      │     │ net_salary           │     │ gst_amount           │     │
│  │  • api_url          │     │ days_worked          │     │ total_amount         │     │
│  │  • auth_type        │     │ total_working_days   │     │ due_date             │     │
│  │  • credentials_ref  │     │ earned_salary        │     │ payment_terms        │     │
│  │  • sync_schedule    │     │ deductions (JSONB)   │     │ po_number            │     │
│  │ status              │     │ components (JSONB)   │     │ grn_number           │     │
│  │ last_sync_at        │     │ employment_status    │     │ status               │     │
│  │ created_at          │     │ synced_at            │     │ (NEW/VERIFIED/       │     │
│  └─────────────────────┘     │ source               │     │  DISCOUNTED/SETTLED/ │     │
│                               └──────────────────────┘     │  DISPUTED/CANCELLED) │     │
│                                                             │ verified_by          │     │
│  ┌─────────────────────────┐                                │ synced_at            │     │
│  │   payment_transactions  │                                │ source               │     │
│  ├─────────────────────────┤                                └──────────────────────┘     │
│  │ id (UUID)               │                                                             │
│  │ loan_id                 │                                                             │
│  │ type (DISBURSE/COLLECT) │                                                             │
│  │ amount                  │                                                             │
│  │ mode                    │                                                             │
│  │ gateway_ref             │                                                             │
│  │ utr_number              │                                                             │
│  │ status                  │                                                             │
│  │ request_payload (JSONB) │                                                             │
│  │ response_payload (JSONB)│                                                             │
│  │ initiated_at            │                                                             │
│  │ completed_at            │                                                             │
│  │ retry_count             │                                                             │
│  └─────────────────────────┘                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Redis Data Structures

| Key Pattern | Data Type | TTL | Purpose |
|-------------|-----------|-----|---------|
| `limit:{borrower_id}:{program_id}` | Hash | None (persistent) | Real-time limit tracking (sanctioned, utilized, available) |
| `eligibility:{borrower_id}` | String (JSON) | 24h | Cached eligibility calculation result |
| `earned_salary:{borrower_id}` | Hash | 24h | Latest earned salary computation |
| `idempotent:{operation}:{ref}` | String | 48h | Idempotency keys for financial operations |
| `rate_limit:{client}:{endpoint}` | Counter | 60s | API rate limiting |
| `session:{token}` | Hash | Configurable | User session data |
| `program_params:{program_id}` | Hash | 1h | Cached program parameters |
| `lock:disbursement:{loan_id}` | String | 30s | Distributed lock for disbursement |
| `lock:limit_update:{borrower_id}` | String | 5s | Lock for concurrent limit updates |

### 5.4 Key Indexes & Partitioning

```sql
-- Critical indexes for high-volume queries
CREATE INDEX idx_loans_borrower_status ON loans(borrower_id, status);
CREATE INDEX idx_loans_program_status ON loans(program_id, status);
CREATE INDEX idx_loans_due_date ON loans(due_date) WHERE status = 'ACTIVE';
CREATE INDEX idx_loan_requests_status ON loan_requests(status, created_at);
CREATE INDEX idx_borrower_limits_program ON borrower_limits(program_id, status);
CREATE INDEX idx_invoices_borrower_status ON invoices(borrower_id, status);
CREATE INDEX idx_salary_records_borrower ON salary_records(borrower_id, pay_period_end DESC);
CREATE INDEX idx_repayments_loan ON repayments(loan_id, received_at);

-- Partitioning strategy for high-volume tables
-- loans: RANGE partition by created_at (monthly)
-- repayments: RANGE partition by received_at (monthly)  
-- audit_events: RANGE partition by created_at (monthly)
-- payment_transactions: RANGE partition by initiated_at (monthly)
```

---

## 6. API Design

### 6.1 API Gateway Routes

| Path Prefix | Target Service | Auth Required | Rate Limit |
|-------------|---------------|---------------|-----------|
| `/api/v1/auth/**` | iam-service | No (login), Yes (others) | 10 req/min (login) |
| `/api/v1/programs/**` | program-service | Yes (Admin/PM) | 100 req/min |
| `/api/v1/anchors/**` | program-service | Yes (Admin/PM/Anchor) | 100 req/min |
| `/api/v1/borrowers/**` | program-service | Yes (All roles) | 200 req/min |
| `/api/v1/loans/**` | lending-service | Yes (All roles) | 500 req/min |
| `/api/v1/disbursements/**` | lending-service | Yes (Admin/PM) | 100 req/min |
| `/api/v1/repayments/**` | lending-service | Yes (Admin/PM) | 200 req/min |
| `/api/v1/integrations/**` | integration-service | Yes (Admin) | 50 req/min |
| `/api/v1/notifications/**` | notification-service | Yes (Admin) | 100 req/min |
| `/api/v1/reports/**` | report-service | Yes (Admin/PM/Compliance) | 20 req/min |
| `/api/v1/portal/borrower/**` | lending-service | Yes (Borrower) | 50 req/min |
| `/api/v1/portal/anchor/**` | program-service | Yes (Anchor) | 50 req/min |

### 6.2 Key API Endpoints

#### Program Service APIs

```yaml
# Program Management
POST   /api/v1/programs                     # Create program
GET    /api/v1/programs                     # List programs (filter, paginate)
GET    /api/v1/programs/{id}                # Get program details
PUT    /api/v1/programs/{id}                # Update program parameters
PATCH  /api/v1/programs/{id}/status         # Change program status
GET    /api/v1/programs/{id}/utilization    # Get real-time utilization

# Anchor Management
POST   /api/v1/anchors                      # Onboard anchor
GET    /api/v1/anchors                      # List anchors
GET    /api/v1/anchors/{id}                 # Get anchor details
PUT    /api/v1/anchors/{id}                 # Update anchor
PATCH  /api/v1/anchors/{id}/status          # Change anchor status
GET    /api/v1/anchors/{id}/borrowers       # List borrowers under anchor

# Borrower Management
POST   /api/v1/borrowers                    # Onboard borrower
GET    /api/v1/borrowers                    # List borrowers (filter, paginate)
GET    /api/v1/borrowers/{id}               # Get borrower details
PUT    /api/v1/borrowers/{id}               # Update borrower
PATCH  /api/v1/borrowers/{id}/status        # Change borrower status
GET    /api/v1/borrowers/{id}/limits        # Get borrower limits across programs
PUT    /api/v1/borrowers/{id}/limits/{programId}  # Update borrower limit

# Eligibility
GET    /api/v1/borrowers/{id}/eligibility   # Check current eligibility
POST   /api/v1/borrowers/{id}/eligibility/refresh  # Force re-evaluation
```

#### Lending Service APIs

```yaml
# Loan Requests (Pay Day)
POST   /api/v1/loans/payday/request         # Create pay day loan request
GET    /api/v1/loans/payday/eligible-amount/{borrowerId}  # Get eligible amount

# Loan Requests (Invoice Discounting)
POST   /api/v1/loans/invoice-discount/request       # Create discounting request
GET    /api/v1/loans/invoice-discount/eligible-invoices/{borrowerId}  # List eligible invoices

# Common Loan Operations
GET    /api/v1/loans                        # List loans (filter: program, borrower, status, product)
GET    /api/v1/loans/{id}                   # Get loan details
PATCH  /api/v1/loans/{id}/approve           # Approve loan (manual review)
PATCH  /api/v1/loans/{id}/reject            # Reject loan (manual review)
POST   /api/v1/loans/{id}/cancel            # Cancel during cooling-off

# Disbursement
POST   /api/v1/disbursements/{loanId}/initiate  # Trigger disbursement
GET    /api/v1/disbursements/{loanId}/status     # Check disbursement status

# Repayment
POST   /api/v1/repayments                   # Record manual repayment
GET    /api/v1/repayments/upcoming          # Upcoming repayments (schedule)
GET    /api/v1/repayments/overdue           # Overdue list

# KFS
GET    /api/v1/loans/{requestId}/kfs        # Get KFS document
POST   /api/v1/loans/{requestId}/kfs/consent  # Record borrower consent on KFS

# Borrower Portal APIs
GET    /api/v1/portal/borrower/dashboard    # Borrower home (limits, active loans)
POST   /api/v1/portal/borrower/request-loan # Request loan (unified)
GET    /api/v1/portal/borrower/loans        # My loans
GET    /api/v1/portal/borrower/statements   # Account statements
```

#### Integration Service APIs

```yaml
# Salary Data (HR Integration)
POST   /api/v1/integrations/hr/sync/{anchorId}       # Trigger salary data sync
GET    /api/v1/integrations/hr/salary/{borrowerId}   # Get latest salary data
POST   /api/v1/integrations/hr/salary/upload         # Manual salary data upload (CSV)

# Invoice Data (ERP Integration)
POST   /api/v1/integrations/erp/sync/{anchorId}      # Trigger invoice sync
GET    /api/v1/integrations/erp/invoices/{anchorId}  # Get invoices from ERP
POST   /api/v1/integrations/erp/invoices/upload      # Manual invoice upload
PATCH  /api/v1/integrations/erp/invoices/{id}/verify # Mark invoice verified

# Payment Operations
POST   /api/v1/integrations/payment/disburse         # Initiate disbursement
POST   /api/v1/integrations/payment/collect          # Initiate collection (NACH)
GET    /api/v1/integrations/payment/{txnId}/status   # Check payment status

# Configuration
POST   /api/v1/integrations/configs                  # Add integration config
GET    /api/v1/integrations/configs/{anchorId}       # Get integration configs
PUT    /api/v1/integrations/configs/{id}             # Update config
POST   /api/v1/integrations/configs/{id}/test        # Test connectivity

# Webhooks (inbound from external systems)
POST   /api/v1/webhooks/payment/callback             # Payment gateway callback
POST   /api/v1/webhooks/hr/{anchorId}/event          # HR system event (termination, etc.)
POST   /api/v1/webhooks/erp/{anchorId}/invoice       # New invoice push from ERP
```

### 6.3 API Response Standards

```json
// Success Response
{
  "status": "SUCCESS",
  "data": { ... },
  "metadata": {
    "timestamp": "2026-05-01T10:30:00Z",
    "requestId": "req-uuid",
    "pagination": { "page": 1, "size": 20, "total": 150 }
  }
}

// Error Response
{
  "status": "ERROR",
  "error": {
    "code": "LIMIT_EXCEEDED",
    "message": "Requested amount exceeds available limit",
    "details": {
      "requested": 50000,
      "available": 30000
    }
  },
  "metadata": {
    "timestamp": "2026-05-01T10:30:00Z",
    "requestId": "req-uuid"
  }
}
```

---

## 7. Core Workflows

### 7.1 Pay Day Loan — End-to-End Flow

```
┌──────────┐    ┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│ Borrower │    │Program Service│    │Lending Service│    │Integration Svc│
│ (Employee)│    │              │    │               │    │              │
└────┬─────┘    └──────┬───────┘    └───────┬───────┘    └──────┬───────┘
     │                  │                    │                    │
     │ 1. Request Loan  │                    │                    │
     │──────────────────────────────────────►│                    │
     │  (amount, purpose)                    │                    │
     │                  │                    │                    │
     │                  │  2. Check Eligibility                   │
     │                  │◄───────────────────│                    │
     │                  │  (borrower_id,     │                    │
     │                  │   program_id)      │                    │
     │                  │                    │                    │
     │                  │  3. Get Earned Salary                   │
     │                  │────────────────────────────────────────►│
     │                  │                    │                    │
     │                  │  4. Salary Data    │                    │
     │                  │◄───────────────────────────────────────│
     │                  │  (earned: ₹45K)    │                    │
     │                  │                    │                    │
     │                  │  5. Eligible: ₹22.5K (50%)             │
     │                  │   Limit OK, No overdue                  │
     │                  │───────────────────►│                    │
     │                  │                    │                    │
     │                  │                    │ 6. Generate KFS    │
     │                  │                    │───────┐            │
     │ 7. Display KFS + │                    │◄──────┘            │
     │    Get Consent   │                    │                    │
     │◄─────────────────────────────────────│                    │
     │                  │                    │                    │
     │ 8. Consent Given │                    │                    │
     │──────────────────────────────────────►│                    │
     │                  │                    │                    │
     │                  │                    │ 9. Auto-Approve    │
     │                  │                    │    (within threshold)
     │                  │                    │───────┐            │
     │                  │                    │◄──────┘            │
     │                  │                    │                    │
     │                  │  10. Block Limit   │                    │
     │                  │◄───────────────────│                    │
     │                  │  (utilized += amt) │                    │
     │                  │                    │                    │
     │                  │                    │ 11. Initiate Disbursement
     │                  │                    │───────────────────►│
     │                  │                    │                    │
     │                  │                    │  12. IMPS Transfer  │
     │                  │                    │                    │──► Bank
     │                  │                    │                    │◄── UTR
     │                  │                    │                    │
     │                  │                    │ 13. Disbursement   │
     │                  │                    │    Confirmed       │
     │                  │                    │◄───────────────────│
     │                  │                    │                    │
     │ 14. Loan Disbursed                    │                    │
     │    (SMS/Email/WA)│                    │                    │
     │◄─────────────────────────────────────│                    │
     │                  │                    │                    │
```

### 7.2 Pay Day Loan — Repayment Flow

```
┌──────────┐    ┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│  Anchor  │    │Program Service│    │Lending Service│    │Integration Svc│
│(Employer)│    │              │    │               │    │              │
└────┬─────┘    └──────┬───────┘    └───────┬───────┘    └──────┬───────┘
     │                  │                    │                    │
     │                  │                    │ 1. Due Date Check  │
     │                  │                    │    (Scheduler: D-1)│
     │                  │                    │───────┐            │
     │                  │                    │◄──────┘            │
     │                  │                    │                    │
     │  2. Salary deduction list for this pay period             │
     │◄─────────────────────────────────────│                    │
     │  (Employee A: ₹22,500 + ₹370 interest)                   │
     │                  │                    │                    │
     │ 3. Confirm Deduction                  │                    │
     │  (salary processed, deducted)         │                    │
     │──────────────────────────────────────►│                    │
     │                  │                    │                    │
     │                  │                    │ 4. Record Repayment│
     │                  │                    │───────┐            │
     │                  │                    │◄──────┘            │
     │                  │                    │                    │
     │                  │  5. Release Limit  │                    │
     │                  │◄───────────────────│                    │
     │                  │  (utilized -= amt) │                    │
     │                  │                    │                    │
     │                  │                    │ 6. Mark Loan CLOSED│
     │                  │                    │───────┐            │
     │                  │                    │◄──────┘            │
     │                  │                    │                    │
     │                  │                    │ 7. Notify Borrower │
     │                  │                    │──────────────────► │
     │                  │                    │                    │ SMS: Loan closed
```

### 7.3 Invoice Discounting — End-to-End Flow

```
┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐
│  Anchor  │  │ Borrower │  │Program Service│  │Lending Service│  │Integration Svc│
│ (Seller) │  │ (Buyer)  │  │              │  │               │  │              │
└────┬─────┘  └────┬─────┘  └──────┬───────┘  └───────┬───────┘  └──────┬───────┘
     │              │               │                   │                  │
     │ 1. Invoice raised & synced from ERP              │                  │
     │─────────────────────────────────────────────────────────────────────►│
     │              │               │                   │                  │
     │              │               │                   │  2. Invoice stored│
     │              │               │                   │◄─────────────────│
     │              │               │                   │  (verified, eligible)
     │              │               │                   │                  │
     │              │  3. View Eligible Invoices         │                  │
     │              │──────────────────────────────────►│                  │
     │              │               │                   │                  │
     │              │  4. Eligible Invoices List         │                  │
     │              │  (Inv#101: ₹5L, Inv#102: ₹8L)    │                  │
     │              │◄──────────────────────────────────│                  │
     │              │               │                   │                  │
     │              │  5. Request Discounting            │                  │
     │              │  (Inv#101: ₹5L, margin 10%        │                  │
     │              │   → request ₹4.5L)                │                  │
     │              │──────────────────────────────────►│                  │
     │              │               │                   │                  │
     │              │               │  6. Validate Limit │                  │
     │              │               │◄──────────────────│                  │
     │              │               │  (available ₹3Cr, │                  │
     │              │               │   OK for ₹4.5L)   │                  │
     │              │               │──────────────────►│                  │
     │              │               │                   │                  │
     │  7. Anchor Confirmation Request                  │                  │
     │◄────────────────────────────────────────────────│                  │
     │  (Confirm Inv#101 is valid, not disputed)       │                  │
     │              │               │                   │                  │
     │  8. Confirmed│               │                   │                  │
     │─────────────────────────────────────────────────►│                  │
     │              │               │                   │                  │
     │              │               │                   │ 9. Generate KFS  │
     │              │               │                   │───────┐          │
     │              │               │                   │◄──────┘          │
     │              │               │                   │                  │
     │              │  10. KFS + Consent                 │                  │
     │              │◄──────────────────────────────────│                  │
     │              │               │                   │                  │
     │              │  11. Consent + e-Sign              │                  │
     │              │──────────────────────────────────►│                  │
     │              │               │                   │                  │
     │              │               │  12. Block Limit  │                  │
     │              │               │◄──────────────────│                  │
     │              │               │                   │                  │
     │              │               │                   │ 13. Disburse     │
     │              │               │                   │─────────────────►│
     │              │               │                   │                  │──► Bank
     │              │               │                   │                  │◄── UTR
     │              │               │                   │ 14. Confirmed    │
     │              │               │                   │◄─────────────────│
     │              │               │                   │                  │
     │ 15. Disbursement Notification│                   │                  │
     │◄────────────────────────────────────────────────│                  │
     │              │               │                   │                  │
     │              │  16. Loan Active Notification     │                  │
     │              │◄──────────────────────────────────│                  │
```

### 7.4 Loan Application State Machine

```
                                    ┌─────────────┐
                                    │   CREATED   │
                                    └──────┬──────┘
                                           │ Eligibility check passed
                                           ▼
                                    ┌─────────────┐
                        ┌──────────│  ELIGIBLE    │──────────┐
                        │          └──────┬──────┘          │
                        │                 │ KFS consent     │ Eligibility
                        │                 ▼                 │ check failed
                        │          ┌─────────────┐          │
                        │          │KFS_CONSENTED│          ▼
                        │          └──────┬──────┘   ┌───────────┐
                        │                 │          │ INELIGIBLE│
                        │     ┌───────────┼──────┐   └───────────┘
                        │     │           │      │
                        │     ▼           ▼      │
                        │ ┌────────┐ ┌────────┐  │
                        │ │ AUTO_  │ │PENDING_│  │
                        │ │APPROVED│ │REVIEW  │  │
                        │ └───┬────┘ └───┬────┘  │
                        │     │          │       │
                        │     │    ┌─────┼─────┐ │
                        │     │    ▼     │     ▼ │
                        │     │┌────────┐│ ┌────────┐
                        │     ││APPROVED││ │REJECTED│
                        │     │└───┬────┘│ └────────┘
                        │     │    │     │
                        │     └────┼─────┘
                        │          │ Disbursement initiated
                        │          ▼
                        │   ┌──────────────┐
                        │   │DISBURSING    │
                        │   └──────┬───────┘
                        │          │ UTR confirmed
                        │          ▼
                        │   ┌──────────────┐
                        │   │   ACTIVE     │◄─── Cooling-off period active
                        │   └──────┬───────┘
                        │          │
                        │    ┌─────┼──────────┐
                        │    │     │          │
                        │    ▼     ▼          ▼
                        │┌───────┐┌────────┐┌──────────┐
                        ││OVERDUE││REPAID  ││CANCELLED │ (during cooling-off)
                        │└───┬───┘└────────┘└──────────┘
                        │    │
                        │    ▼
                        │┌──────────────┐
                        ││OVERDUE_REPAID│
                        │└──────────────┘
                        │
                        └──────────────── All terminal states also have
                                          WRITTEN_OFF (for NPA cases)
```

### 7.5 Limit Management Flow

```
Limit Hierarchy Enforcement:

1. On Loan Request:
   ├── Check borrower's available_limit >= requested_amount
   ├── Check program's (total_utilized + requested) <= program_limit
   ├── Check anchor's (total_utilized + requested) <= anchor_limit
   └── If all pass → Block limit (optimistic lock with Redis)

2. On Disbursement Confirmed:
   └── Confirm limit block (permanent until repayment)

3. On Repayment:
   ├── Release limit: available_limit += repaid_principal
   ├── Update program utilization (decrement)
   └── Publish event: LIMIT_RELEASED

4. On Limit Freeze (overdue/termination):
   ├── Set borrower limit status = FROZEN
   ├── available_limit = 0 (regardless of actual available)
   └── Block all new loan requests

5. Limit Recalculation (scheduled):
   ├── For Pay Day: Re-fetch salary → recalculate eligible amount
   ├── For Invoice Discounting: Re-check anchor credit → adjust limit
   └── If new limit < utilized: mark for review (don't auto-reduce)
```

---

## 8. Integration Architecture

### 8.1 Integration Service — Adapter Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                    INTEGRATION SERVICE                            │
│                                                                   │
│  ┌────────────────────────────────────────────────────────┐      │
│  │              Integration Router                         │      │
│  │   (Routes requests to appropriate adapter based on      │      │
│  │    anchor's integration_config.provider)                │      │
│  └────────────┬───────────┬───────────┬──────────────────┘      │
│               │           │           │                          │
│        ┌──────┴───┐ ┌─────┴────┐ ┌────┴──────┐                  │
│        │HR Adapters│ │ERP Adapt.│ │Payment GW │                  │
│        ├──────────┤ ├──────────┤ ├───────────┤                  │
│        │SAP SF    │ │SAP ERP   │ │Razorpay   │                  │
│        │Darwinbox │ │Oracle    │ │Cashfree   │                  │
│        │greytHR   │ │Tally     │ │NACH/NPCI  │                  │
│        │Keka      │ │Zoho Books│ │IMPS/NEFT  │                  │
│        │Custom API│ │Custom API│ │UPI        │                  │
│        │File(SFTP)│ │File(SFTP)│ │           │                  │
│        └──────────┘ └──────────┘ └───────────┘                  │
│                                                                   │
│        ┌───────────┐ ┌──────────┐ ┌───────────┐                 │
│        │Verification│ │e-Sign   │ │ LoS Bridge│                 │
│        ├───────────┤ ├──────────┤ ├───────────┤                 │
│        │PAN (NSDL) │ │Leegality │ │KYC API    │                 │
│        │GST (NIC)  │ │eMsigner  │ │Bureau API │                 │
│        │Bank Verify│ │          │ │Notif API  │                 │
│        │CIN (MCA)  │ │          │ │LMS API    │                 │
│        └───────────┘ └──────────┘ └───────────┘                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 HR System Integration (Pay Day Loan)

```java
// Adapter Interface
public interface HrSystemAdapter {
    SalaryDataResponse fetchCurrentSalary(String employeeId, String anchorId);
    List<SalaryDataResponse> fetchBulkSalary(String anchorId, LocalDate periodStart, LocalDate periodEnd);
    EmploymentStatusResponse checkEmploymentStatus(String employeeId, String anchorId);
    void registerWebhook(String anchorId, String eventType, String callbackUrl);
}

// Salary Data Structure (common across all HR adapters)
record SalaryDataResponse(
    String employeeId,
    BigDecimal grossSalary,
    BigDecimal netSalary,
    int totalWorkingDays,
    int daysWorked,
    BigDecimal earnedSalary,        // Computed: (netSalary / totalWorkingDays) * daysWorked
    Map<String, BigDecimal> components,  // basic, hra, conveyance, special_allowance, etc.
    Map<String, BigDecimal> deductions,  // pf, esi, professional_tax, tds, etc.
    String employmentStatus,         // ACTIVE, NOTICE_PERIOD, TERMINATED
    LocalDate periodStart,
    LocalDate periodEnd,
    Instant syncedAt
)
```

### 8.3 ERP System Integration (Invoice Discounting)

```java
// Adapter Interface
public interface ErpSystemAdapter {
    List<InvoiceDataResponse> fetchInvoices(String anchorId, LocalDate fromDate, LocalDate toDate);
    InvoiceDataResponse fetchInvoice(String anchorId, String invoiceNumber);
    Optional<PurchaseOrderResponse> fetchPurchaseOrder(String anchorId, String poNumber);
    Optional<GrnResponse> fetchGrn(String anchorId, String grnNumber);
    void registerWebhook(String anchorId, String eventType, String callbackUrl);
}

// Invoice Data Structure
record InvoiceDataResponse(
    String invoiceNumber,
    LocalDate invoiceDate,
    BigDecimal invoiceAmount,
    BigDecimal gstAmount,
    BigDecimal totalAmount,
    String buyerGstin,
    String buyerName,
    String sellerGstin,
    LocalDate dueDate,
    int paymentTermDays,
    String poNumber,             // Purchase Order reference
    String grnNumber,            // Goods Receipt Note reference
    String status,               // OPEN, PARTIALLY_PAID, PAID, CANCELLED, DISPUTED
    Map<String, Object> lineItems,
    Instant syncedAt
)
```

### 8.4 Payment Integration

```java
// Unified Payment Interface
public interface PaymentGatewayAdapter {
    DisbursementResponse initiateDisbursement(DisbursementRequest request);
    PaymentStatusResponse checkStatus(String transactionId);
    CollectionResponse initiateCollection(CollectionRequest request);
    NachMandateResponse registerNachMandate(NachMandateRequest request);
    NachMandateResponse checkMandateStatus(String mandateId);
}

// Disbursement modes based on amount:
// < ₹2L → UPI (instant, no charges)
// < ₹5L → IMPS (instant, nominal charges)
// ≥ ₹5L → NEFT (batch, no charges) or RTGS (instant, for > ₹2L)
```

### 8.5 BillionTech LoS Bridge

```java
// LoS Integration Interface
public interface LosBridgeAdapter {
    // KYC — leverage LoS enrollment service
    KycResponse triggerKyc(String customerId, KycType kycType);
    KycStatusResponse getKycStatus(String customerId);
    
    // Credit Bureau — leverage LoS credit decision service
    BureauResponse pullCreditBureau(String pan, BureauType bureau);
    
    // Notification — leverage LoS notification service (optional)
    void sendNotification(NotificationRequest request);
    
    // LMS Handover — for post-disbursement loan servicing
    LmsHandoverResponse handoverToLms(LmsHandoverRequest request);
}
```

---

## 9. Security Architecture

### 9.1 Authentication & Authorization

```
┌─────────────────────────────────────────────────────────────┐
│                    SECURITY LAYERS                            │
│                                                              │
│  Layer 1: API Gateway                                        │
│  ├── JWT validation (RS256, 15-min access token)            │
│  ├── Rate limiting (per client, per endpoint)               │
│  ├── CORS (whitelist origins)                               │
│  ├── OWASP headers (CSP, X-Frame-Options, etc.)            │
│  └── IP whitelist (for Anchor APIs)                         │
│                                                              │
│  Layer 2: Service-Level                                      │
│  ├── RBAC (Role-Based Access Control)                       │
│  │   ├── PLATFORM_ADMIN (full access)                       │
│  │   ├── CREDIT_MANAGER (program CRUD, approvals)           │
│  │   ├── CREDIT_ANALYST (review, approve/reject)            │
│  │   ├── ANCHOR_ADMIN (own program view, data upload)       │
│  │   ├── BORROWER (self-service only)                       │
│  │   ├── ACCOUNTS_OFFICER (disbursement, reconciliation)    │
│  │   └── COMPLIANCE_OFFICER (reports, audit)                │
│  ├── Resource-level authorization (own-data only)           │
│  └── API Key auth for system-to-system (integration svc)    │
│                                                              │
│  Layer 3: Data-Level                                         │
│  ├── Column-level encryption (PAN, Aadhaar, bank account)   │
│  ├── PII masking in logs                                    │
│  ├── Audit trail (immutable, tamper-evident)                │
│  └── Data retention policies (7 years for financial records) │
│                                                              │
│  Layer 4: Infrastructure                                     │
│  ├── TLS 1.3 for all inter-service communication            │
│  ├── Database encryption at rest (AES-256)                  │
│  ├── Secret management (Vault / AWS Secrets Manager)         │
│  ├── Network segmentation (service mesh)                    │
│  └── WAF (Web Application Firewall) at edge                 │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Data Classification & Protection

| Data Type | Classification | Storage | Access |
|-----------|---------------|---------|--------|
| PAN, Aadhaar | Highly Sensitive | Encrypted (AES-256), masked in UI (last 4) | Compliance officer, system only |
| Bank Account | Sensitive | Encrypted, masked (last 4 digits) | Treasury, system for disbursement |
| Salary Data | Confidential | Encrypted at rest | Borrower (own), Program Manager |
| Invoice Data | Confidential | Encrypted at rest | Anchor (own), Borrower (own), PM |
| Loan Details | Restricted | Standard DB encryption | Role-based, own-data for borrower |
| Audit Logs | Internal | Immutable, append-only | Compliance officer, Admin |

### 9.3 API Security for External Integrations

- **HR/ERP Systems**: OAuth 2.0 Client Credentials flow, with client certificates for mutual TLS
- **Payment Gateway**: API Key + HMAC signature on request body, IP whitelist
- **BillionTech LoS**: Service-to-service JWT (machine token), mutual TLS
- **Webhook Verification**: HMAC-SHA256 signature validation on all inbound webhooks

---

## 10. Frontend Architecture

### 10.1 Application Structure

```
program-lending-platform/
├── packages/
│   ├── platform-ui/          # Admin & Program Manager UI (:3000)
│   │   ├── src/
│   │   │   ├── pages/        # Page components (React Router)
│   │   │   ├── components/   # Shared UI components
│   │   │   ├── hooks/        # Custom hooks (usePrograms, useLoans, etc.)
│   │   │   ├── services/     # API client layer (Axios)
│   │   │   ├── stores/       # Client state (Zustand)
│   │   │   ├── types/        # TypeScript interfaces
│   │   │   └── utils/        # Utilities, formatters
│   │   ├── package.json
│   │   └── vite.config.ts
│   │
│   ├── anchor-portal/        # Anchor (Employer/Seller) UI (:3001)
│   │   ├── src/
│   │   │   ├── pages/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   ├── services/
│   │   │   └── types/
│   │   ├── package.json
│   │   └── vite.config.ts
│   │
│   ├── borrower-portal/      # Borrower (Employee/Buyer) UI (:3002)
│   │   ├── src/              # Mobile-first responsive design
│   │   │   ├── pages/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   ├── services/
│   │   │   └── types/
│   │   ├── package.json
│   │   └── vite.config.ts
│   │
│   └── shared/               # Shared component library
│       ├── ui/               # Design system components
│       ├── utils/            # Shared utilities
│       └── types/            # Shared TypeScript types
│
├── package.json              # Monorepo root (npm workspaces)
└── turbo.json                # Turborepo config (parallel builds)
```

### 10.2 Key UI Flows

#### Platform Admin — Program Creation Flow

```
[Programs List] → [+ Create Program] → [Wizard Step 1: Basic Info]
  ├── Product Type: PAY_DAY_LOAN | INVOICE_DISCOUNTING
  ├── Program Name
  ├── Select Anchor (from onboarded anchors)
  └── [Next →]

[Wizard Step 2: Limits & Parameters]
  ├── Program Limit (₹)
  ├── Anchor Limit (₹)
  ├── Max Borrower Limit (₹)
  ├── Interest Rate Range (min% — max%)
  ├── Default Interest Rate (%)
  ├── Max Tenure (days)
  ├── Margin % (for Invoice Discounting)
  └── [Next →]

[Wizard Step 3: Eligibility Rules]
  ├── (PAY_DAY) Min Employment Tenure (months)
  ├── (PAY_DAY) Min Net Salary (₹)
  ├── (PAY_DAY) Eligibility % of Earned Salary
  ├── (INVOICE) Min Purchase History (months)
  ├── (INVOICE) Min Invoice Value (₹)
  ├── (INVOICE) Max Invoice Age (days)
  ├── Max Concurrent Loans
  ├── Cooling-off Period (days)
  └── [Next →]

[Wizard Step 4: Auto-Approval Rules]
  ├── Auto-approve below amount (₹)
  ├── Auto-approve below % of limit
  ├── Require anchor confirmation: Yes/No
  └── [Create Program ✓]
```

#### Borrower Portal — Loan Request (Pay Day)

```
[Home Dashboard]
  ├── Available Limit: ₹22,500
  ├── Earned This Month: ₹45,000
  ├── Active Loans: 0
  └── [Request Advance →]

[Request Screen]
  ├── Amount Slider: ₹5,000 ——— ₹22,500
  ├── Selected: ₹15,000
  ├── Interest (18% p.a. × 15 days): ₹111
  ├── Processing Fee: ₹0
  ├── Total Repayable: ₹15,111
  ├── Repayment Date: May 31, 2026
  ├── [View Full KFS Document]
  └── [☐ I agree to the terms → Confirm Request]

[Confirmation]
  ├── Status: Approved ✓
  ├── Disbursing to: HDFC A/c ****1234
  ├── Expected Credit: Within 15 minutes
  └── Loan Reference: PDL-2026-05-00142
```

### 10.3 Component Library (Shared Design System)

| Component | Usage |
|-----------|-------|
| `<LimitBar />` | Visual progress bar showing utilized/available/sanctioned |
| `<ProgramCard />` | Summary card for program (utilization, status, key metrics) |
| `<LoanTimeline />` | Vertical timeline showing loan state transitions |
| `<KFSViewer />` | Formatted KFS document display with consent checkbox |
| `<AmountSlider />` | Slider for selecting loan amount within eligible range |
| `<StatusBadge />` | Colored badges for various statuses |
| `<DataTable />` | Configurable table with sort, filter, export |
| `<MetricCard />` | Dashboard metric display (value, trend, sparkline) |
| `<InvoiceList />` | Selectable list of invoices for discounting |
| `<AnchorStepper />` | Multi-step onboarding wizard |

---

## 11. Deployment Architecture

### 11.1 Container Architecture

```yaml
# docker-compose.yml structure
services:
  # Infrastructure
  postgres:        # PostgreSQL 16, all schemas
  redis:           # Redis 7.2, persistence enabled
  rabbitmq:        # RabbitMQ 3.13, management UI
  minio:           # MinIO, 4 buckets

  # Application Services
  discovery-service:    # Eureka :8761
  api-gateway:         # Gateway :8080
  iam-service:         # IAM :8081
  program-service:     # Programs :8082
  lending-service:     # Lending :8083
  integration-service: # Integration :8084
  notification-service: # Notifications :8085
  report-service:      # Reports :8086

  # Frontend
  platform-ui:         # Admin UI :3000
  anchor-portal:       # Anchor UI :3001
  borrower-portal:     # Borrower UI :3002

  # Monitoring (optional profile)
  prometheus:          # Metrics :9090
  grafana:            # Dashboards :3003
```

### 11.2 Production Deployment (Kubernetes)

```
┌─────────────────────────────────────────────────────────────────────┐
│                        KUBERNETES CLUSTER                             │
│                                                                      │
│  ┌─────────────────┐   ┌────────────────────────────────────────┐   │
│  │  Ingress (Nginx) │   │            Namespaces                  │   │
│  │  + TLS termination│   │                                        │   │
│  └────────┬─────────┘   │  plp-services/                         │   │
│           │              │  ├── api-gateway (2 replicas, HPA)     │   │
│           │              │  ├── iam-service (2 replicas)          │   │
│           │              │  ├── program-service (3 replicas, HPA) │   │
│           │              │  ├── lending-service (3 replicas, HPA) │   │
│           │              │  ├── integration-service (2 replicas)  │   │
│           │              │  ├── notification-service (2 replicas) │   │
│           │              │  └── report-service (2 replicas)       │   │
│           │              │                                        │   │
│           │              │  plp-frontend/                         │   │
│           │              │  ├── platform-ui (2 replicas)          │   │
│           │              │  ├── anchor-portal (2 replicas)        │   │
│           │              │  └── borrower-portal (3 replicas, HPA) │   │
│           │              │                                        │   │
│           │              │  plp-infra/                            │   │
│           │              │  ├── postgres (StatefulSet, 1 primary  │   │
│           │              │  │   + 1 read replica)                 │   │
│           │              │  ├── redis (Sentinel, 3 nodes)         │   │
│           │              │  ├── rabbitmq (cluster, 3 nodes)       │   │
│           │              │  └── minio (distributed, 4 nodes)      │   │
│           │              │                                        │   │
│           │              │  plp-monitoring/                       │   │
│           │              │  ├── prometheus                        │   │
│           │              │  ├── grafana                           │   │
│           │              │  ├── loki (logging)                    │   │
│           │              │  └── zipkin (tracing)                  │   │
│           │              └────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.3 Resource Estimates

| Service | CPU (Request/Limit) | Memory (Request/Limit) | Replicas |
|---------|--------------------|-----------------------|----------|
| api-gateway | 500m / 1000m | 512Mi / 1Gi | 2-4 (HPA) |
| iam-service | 250m / 500m | 384Mi / 768Mi | 2 |
| program-service | 500m / 1000m | 512Mi / 1Gi | 2-4 (HPA) |
| lending-service | 500m / 1500m | 768Mi / 1.5Gi | 3-6 (HPA) |
| integration-service | 500m / 1000m | 512Mi / 1Gi | 2-3 |
| notification-service | 250m / 500m | 384Mi / 768Mi | 2 |
| report-service | 500m / 1000m | 1Gi / 2Gi | 2 |
| PostgreSQL | 2000m / 4000m | 4Gi / 8Gi | 1+1 replica |
| Redis | 500m / 1000m | 1Gi / 2Gi | 3 (Sentinel) |
| RabbitMQ | 500m / 1000m | 1Gi / 2Gi | 3 (cluster) |

---

## 12. Monitoring & Observability

### 12.1 Metrics (Prometheus + Grafana)

| Dashboard | Key Metrics |
|-----------|-------------|
| **Business Dashboard** | Daily disbursements (count/value), Daily collections, Outstanding portfolio, Overdue %, STP rate, Avg TAT |
| **Program Health** | Per-program utilization %, Active borrowers, New loans today, Overdue loans, Limit breaches |
| **Service Health** | Request rate, Error rate, Latency (p50/p95/p99), Circuit breaker state, Queue depth |
| **Integration Health** | HR sync success rate, ERP sync status, Payment success rate, External API latency |
| **Infrastructure** | CPU/Memory utilization, DB connections, Redis memory, RabbitMQ queue depth, Disk I/O |

### 12.2 Alerting Rules

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| Disbursement Failure Rate > 5% | 5 failures in 10 minutes | Critical | Page on-call, auto-pause disbursements |
| Lending Service Latency > 5s | p95 > 5s for 5 minutes | High | Notify team, check DB/Redis |
| HR Sync Failed | Sync failed for an anchor for > 6 hours | Medium | Notify PM, use fallback salary |
| Program Limit > 90% | Utilization crosses 90% | Medium | Notify PM for limit review |
| Overdue Spike | Overdue count increase > 20% day-over-day | High | Notify Risk team |
| Queue Backlog | RabbitMQ queue > 10K messages | High | Scale consumers |
| DB Connection Pool Exhausted | Available connections < 5 | Critical | Alert infra, check for leaks |

### 12.3 Distributed Tracing

- Every request gets a `X-Request-Id` (UUID) propagated across all services
- Trace spans for: API Gateway → Service → DB/Redis/RabbitMQ/External API
- Payment transactions traced end-to-end: request → gateway call → UTR confirmation
- Slow query logging: queries > 100ms logged with full trace context

### 12.4 Audit Logging

```json
// Audit event structure (stored in audit_events table + shipped to log aggregator)
{
  "event_id": "uuid",
  "timestamp": "2026-05-01T10:30:00Z",
  "actor": {
    "user_id": "uuid",
    "role": "CREDIT_MANAGER",
    "ip_address": "10.0.1.42",
    "user_agent": "..."
  },
  "action": "LOAN_APPROVED",
  "resource": {
    "type": "LOAN_REQUEST",
    "id": "uuid",
    "program_id": "uuid"
  },
  "before": { "status": "PENDING_REVIEW" },
  "after": { "status": "APPROVED", "remarks": "Within policy limits" },
  "metadata": {
    "request_id": "uuid",
    "service": "lending-service"
  }
}
```

---

## 13. Scalability & Performance

### 13.1 Performance Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Redis for limit management** | Sub-millisecond limit checks; Redis atomic operations prevent overselling |
| **Java Virtual Threads** | Handle 50K+ concurrent requests without thread pool exhaustion |
| **CQRS for reporting** | Read replicas serve report queries without impacting transactional workload |
| **Event-driven notifications** | Decouple notification delivery from loan processing flow |
| **Database connection pooling** | HikariCP with optimal pool sizing per service |
| **Batch operations** | Salary sync, interest accrual, overdue marking run as batch jobs |
| **Table partitioning** | Monthly partitions for loans, repayments, audit (efficient querying + archival) |
| **Idempotency with Redis** | Prevent duplicate disbursements/collections even with retries |

### 13.2 Scaling Strategy

```
Load Pattern:
- Salary data sync: Daily batch (peak at 9 AM — employer payroll sync)
- Loan requests (PDL): Peaked mid-month and end-of-month
- Invoice discounting: Spread throughout month
- Repayments: End-of-month salary dates (25th-1st)

Scaling Approach:
┌─────────────────────────────────────────────────────────┐
│  lending-service: HPA based on request rate + CPU       │
│  ├── Min: 3 replicas                                    │
│  ├── Max: 6 replicas                                    │
│  └── Scale trigger: CPU > 70% or requests > 500/s       │
│                                                          │
│  program-service: HPA based on request rate             │
│  ├── Min: 2 replicas                                    │
│  ├── Max: 4 replicas                                    │
│  └── Scale trigger: requests > 300/s                     │
│                                                          │
│  integration-service: Scale based on queue depth         │
│  ├── Min: 2 replicas                                    │
│  ├── Max: 4 replicas                                    │
│  └── Scale trigger: RabbitMQ queue > 1000 messages       │
│                                                          │
│  borrower-portal: HPA based on concurrent connections   │
│  ├── Min: 3 replicas                                    │
│  ├── Max: 8 replicas                                    │
│  └── Scale trigger: connections > 2000 per pod           │
└─────────────────────────────────────────────────────────┘
```

### 13.3 Caching Strategy

| Cache | Strategy | TTL | Invalidation |
|-------|----------|-----|-------------|
| Program parameters | Read-through | 1 hour | On program update (event) |
| Borrower eligibility | Computed + cached | 24 hours | On salary sync / limit change |
| Earned salary | Computed + cached | Until next sync | On HR data sync |
| Eligible invoices | Computed + cached | 1 hour | On new invoice / discounting event |
| User sessions | Write-through | Token lifetime | On logout / expiry |
| API rate limits | Counter | 60 seconds | Auto-expire |

---

## 14. Disaster Recovery & High Availability

### 14.1 HA Design

| Component | HA Mechanism | Recovery |
|-----------|-------------|----------|
| Application Services | Multiple replicas + health checks + rolling updates | Auto-restart via K8s |
| PostgreSQL | Streaming replication (primary + standby) | Automatic failover (Patroni) |
| Redis | Redis Sentinel (3 nodes) | Automatic failover |
| RabbitMQ | Mirrored queues across cluster | Automatic failover |
| MinIO | Distributed mode (4+ nodes, erasure coding) | Self-healing |
| API Gateway | Multiple replicas behind load balancer | Health check based routing |

### 14.2 Backup Strategy

| Data | Method | Frequency | Retention |
|------|--------|-----------|-----------|
| PostgreSQL | pg_dump (full) + WAL archiving (continuous) | Daily full + continuous WAL | 30 days + 7 years (regulatory) |
| Redis | RDB snapshots + AOF | Every 5 minutes | 7 days |
| MinIO (documents) | Cross-region replication | Continuous | Indefinite (7-year regulatory minimum) |
| Configuration | Git (infrastructure as code) | On change | Indefinite |

### 14.3 Disaster Recovery

| Metric | Target | Strategy |
|--------|--------|----------|
| RPO (Recovery Point Objective) | < 1 hour | WAL archiving + Redis AOF |
| RTO (Recovery Time Objective) | < 4 hours | Standby environment + automated failover scripts |
| Failover (single service) | < 30 seconds | K8s health checks + auto-restart |
| Failover (database) | < 2 minutes | Patroni automatic failover |

---

## 15. Migration & Rollout Strategy

### 15.1 Development Phases

```
Phase 1: Foundation (Weeks 1-4)
├── Project setup (monorepo, CI/CD, Docker Compose)
├── IAM Service (auth, RBAC, user management)
├── Program Service (program CRUD, anchor/borrower onboarding, limit engine)
├── Database schemas + Flyway migrations
├── API Gateway + Service Discovery
├── Shared component library (React)
└── Platform UI (program management screens)

Phase 2: Pay Day Loan (Weeks 5-8)
├── HR Integration adapters (mock + 1 real adapter)
├── Earned salary calculation engine
├── Lending Service (loan request, eligibility, approval)
├── KFS generation
├── Payment integration (disbursement)
├── Repayment processing (salary deduction flow)
├── Borrower Portal (request loan, view status)
├── Anchor Portal (view employees, upload salary)
└── End-to-end testing with mock HR system

Phase 3: Invoice Discounting (Weeks 9-12)
├── ERP Integration adapters (mock + 1 real adapter)
├── Invoice management (upload, verify, three-way match)
├── Discounting engine (margin, discount rate calculation)
├── Payment integration (disbursement to buyer/seller)
├── Repayment processing (invoice due date collection)
├── Invoice-specific UI screens
├── Anchor Portal (invoice upload, confirmation)
└── End-to-end testing with mock ERP system

Phase 4: Production Readiness (Weeks 13-16)
├── Notification service (SMS/Email/WhatsApp)
├── Report service (operational + regulatory)
├── Security hardening (encryption, audit, penetration testing)
├── Performance testing (load test 50K daily transactions)
├── BillionTech LoS integration (KYC, bureau, notifications)
├── UAT with pilot anchor (1 employer + 1 seller)
├── Documentation (API docs, user guides, runbooks)
└── Production deployment + monitoring setup
```

### 15.2 Rollout Strategy

| Stage | Scope | Duration | Success Criteria |
|-------|-------|----------|-----------------|
| **Pilot** | 1 employer (PDL) + 1 seller (ID), 50 borrowers each | 4 weeks | Zero failed disbursements, <5% support tickets |
| **Limited GA** | 5 anchors, 500 borrowers | 4 weeks | STP > 80%, NPA < 1%, system uptime > 99.5% |
| **Full GA** | Open for new anchor onboarding | Ongoing | STP > 90%, processing capacity > 10K/day |

### 15.3 Feature Flags

| Flag | Purpose | Default |
|------|---------|---------|
| `ff.payday.enabled` | Enable/disable Pay Day Loan product | true |
| `ff.invoice_discounting.enabled` | Enable/disable Invoice Discounting | true |
| `ff.auto_approval.enabled` | Enable auto-approval engine | true |
| `ff.nach_collection.enabled` | Enable NACH auto-debit | false (Phase 2) |
| `ff.los_integration.enabled` | Enable LoS API calls | false (Phase 4) |
| `ff.three_way_match.enabled` | Enforce PO-GRN-Invoice match | false (pilot relaxed) |
| `ff.credit_bureau.enabled` | Enable bureau pull for high-ticket | false (Phase 4) |

---

## Appendix A: Project Structure

```
program-lending-platform/
├── pom.xml                          # Parent Maven POM (multi-module)
├── docker-compose.yml               # Full stack (dev)
├── docker-compose.infra.yml         # Infrastructure only
├── init-databases.sql               # Schema creation
├── start.sh / stop.sh               # Dev convenience scripts
│
├── services/
│   ├── discovery-service/           # Eureka (:8761)
│   │   └── src/main/java/...
│   ├── api-gateway/                 # Gateway (:8080)
│   │   └── src/main/java/...
│   ├── iam-service/                 # IAM (:8081)
│   │   └── src/main/java/...
│   ├── program-service/             # Programs, Anchors, Borrowers, Limits (:8082)
│   │   └── src/main/java/com/plp/program/
│   │       ├── controller/
│   │       ├── service/
│   │       │   ├── program/
│   │       │   ├── anchor/
│   │       │   ├── borrower/
│   │       │   ├── limit/
│   │       │   └── eligibility/
│   │       ├── model/
│   │       │   ├── entity/
│   │       │   ├── dto/
│   │       │   └── enums/
│   │       ├── repository/
│   │       ├── config/
│   │       └── exception/
│   ├── lending-service/             # Loans, Disbursement, Repayment (:8083)
│   │   └── src/main/java/com/plp/lending/
│   │       ├── controller/
│   │       ├── service/
│   │       │   ├── request/         # Loan request processing
│   │       │   ├── approval/        # Auto + manual approval
│   │       │   ├── disbursement/    # Disbursement orchestration
│   │       │   ├── repayment/       # Repayment processing
│   │       │   ├── interest/        # Interest calculation
│   │       │   ├── kfs/             # KFS generation
│   │       │   └── overdue/         # Overdue management
│   │       ├── model/
│   │       ├── repository/
│   │       └── statemachine/        # Loan state transitions
│   ├── integration-service/         # External system adapters (:8084)
│   │   └── src/main/java/com/plp/integration/
│   │       ├── controller/
│   │       ├── adapter/
│   │       │   ├── hr/              # HR system adapters
│   │       │   ├── erp/             # ERP system adapters
│   │       │   ├── payment/         # Payment gateway adapters
│   │       │   ├── verification/    # PAN, GST, Bank verification
│   │       │   ├── esign/           # e-Sign adapters
│   │       │   └── los/             # BillionTech LoS bridge
│   │       ├── service/
│   │       ├── model/
│   │       └── webhook/             # Inbound webhook handlers
│   ├── notification-service/        # Notifications (:8085)
│   │   └── src/main/java/...
│   └── report-service/              # Reports & Analytics (:8086)
│       └── src/main/java/...
│
├── frontend/
│   ├── packages/
│   │   ├── platform-ui/             # Admin React app (:3000)
│   │   ├── anchor-portal/           # Anchor React app (:3001)
│   │   ├── borrower-portal/         # Borrower React app (:3002)
│   │   └── shared/                  # Shared components
│   ├── package.json                 # Workspace root
│   └── turbo.json
│
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/dashboards/
│
└── docs/
    ├── BRD.md
    ├── HLD.md
    ├── API.md
    └── runbook.md
```

---

## Appendix B: Technology Decision Records

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Monorepo vs Multi-repo | Monorepo | Multi-repo | Aligned with LoS pattern; shared libraries; atomic PRs |
| Frontend framework | React + Vite | Next.js (like LoS) | User specified React; Vite for faster builds; SPAs don't need SSR |
| State management | React Query + Zustand | Redux, MobX | React Query for server state; Zustand for lightweight client state |
| Async processing | RabbitMQ | Kafka, Redis Streams | Aligned with LoS; sufficient for volume; simpler ops |
| Limit management | Redis (primary) + PostgreSQL (persistent) | PostgreSQL only | Sub-ms limit checks critical for STP; Redis atomic operations |
| Multi-tenancy | Schema-per-service | DB-per-service, Row-level | Aligned with LoS pattern; simpler ops; sufficient isolation |
| API versioning | URL path (/v1/) | Header-based | Simpler to route; easier to deprecate |
| Authentication | JWT (RS256) | Session-based, OAuth2 opaque | Stateless; aligned with LoS; inter-service verification without DB call |

---

*End of High-Level Design Document*
