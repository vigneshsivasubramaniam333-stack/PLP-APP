# Program Lending Platform — Complete Documentation

## 1. Overview

The **Program Lending Platform (PLP)** is a standalone microservices-based application that supports two lending products:

1. **Pay Day Loan (PDL)** — Salary-linked lending where employees borrow against accumulated wages
2. **Invoice Discounting (ID)** — Purchase bill discounting where buyers borrow against verified invoices

Both products use a **Program** concept with a hierarchy:
- **Lender** → provides capital
- **Anchor** → corporate facilitator (employer for PDL, seller for ID)
- **Borrower** → loan recipient (employee for PDL, buyer for ID)

Each program has anchor-level and borrower-level limits and parameters.

---

## 2. Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0 |
| Frontend | React 18, TypeScript, Vite 6, Tailwind CSS v4 |
| Database | PostgreSQL 16 (schema-per-service) |
| Cache | Redis 7.2 (limit management) |
| Messaging | RabbitMQ 3.13 (events, notifications, audit) |
| Object Storage | MinIO (document storage) |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway MVC |
| Build | Maven (multi-module), npm workspaces |

---

## 3. Architecture

### 3.1 Microservices

| # | Service | Port | Schema | Purpose |
|---|---------|------|--------|---------|
| 1 | **discovery-service** | 8761 | — | Eureka service registry |
| 2 | **api-gateway** | 8080 | — | JWT auth, routing, CORS, rate limiting |
| 3 | **iam-service** | 8081 | plp_iam | Users, roles, JWT tokens, login/register |
| 4 | **program-service** | 8082 | plp_program | Programs, anchors, borrowers, limits, salary data, invoices |
| 5 | **lending-service** | 8083 | plp_lending | Loans, eligibility, disbursement, repayment, KFS |
| 6 | **integration-service** | 8084 | plp_integration | Adapter pattern for HR/ERP/payment systems |
| 7 | **notification-service** | 8085 | plp_notification | Event-driven IN_APP notifications with templates |
| 8 | **report-service** | 8086 | plp_report | Cross-service reports, CSV export, audit trail |

### 3.2 Frontend Portals

| Portal | Port | Color | Users |
|--------|------|-------|-------|
| **Platform Admin** | 3000 | Blue (blue-600) | Platform administrators |
| **Anchor Portal** | 3001 | Green (emerald-600) | Anchor company users |
| **Borrower Portal** | 3002 | Sky (sky-600) | Individual borrowers |

### 3.3 Infrastructure (Docker Compose)

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL 16 | 5432 | Primary database (6 schemas) |
| Redis 7.2 | 6379 | Real-time limit caching |
| RabbitMQ 3.13 | 5672 / 15672 | Event messaging / Management UI |
| MinIO | 9000 / 9001 | Object storage / Console |

---

## 4. Database Schema

### 4.1 plp_iam
- `users` — email, password_hash, role (PLATFORM_ADMIN, ANCHOR_USER, BORROWER), linked_entity_id

### 4.2 plp_program
- `programs` — programCode, productType (PAY_DAY_LOAN, INVOICE_DISCOUNTING), anchorId, status, limits, interest rates
- `anchors` — name, type (EMPLOYER, SELLER), registration details
- `borrowers` — name, type (EMPLOYEE, BUYER), borrower_code, KYC details
- `borrower_limits` — per-program limits (sanctioned, utilized, available, blocked), maxConcurrentLoans
- `employee_salary_data` — pay period, gross/net salary, daysWorked, eligibleAmount, eligibilityPercent
- `invoices` — invoice number, amount, PO/GRN (three-way match), margin, eligible/discounted/available amounts, status workflow

### 4.3 plp_lending
- `loans` — loanNumber, borrowerId, programId, status workflow, amounts (requested, sanctioned, disbursed, outstanding), dates, interestRate, tenure
- `disbursements` — loan disbursement records
- `repayments` — repayment transaction records

### 4.4 plp_integration
- `integration_configs` — adapter configurations for HR/ERP/Payment systems

### 4.5 plp_notification
- `notification_templates` — templateCode, channel (IN_APP), subject/body with {{variable}} placeholders
- `notifications` — recipient, subject, body, status (PENDING→SENT→DELIVERED/FAILED), retry tracking

### 4.6 plp_report
- `audit_trail` — entityType, entityId, action, actorId, oldValues/newValues (jsonb), timestamps
- `report_definitions` — configurable report types
- `generated_reports` — cached report outputs

---

## 5. Features Built

### 5.1 Phase 1 — Foundation
- [x] Maven multi-module project scaffolding (8 services)
- [x] Eureka service discovery + API Gateway with JWT filter
- [x] IAM service with BCrypt password hashing, role-based access
- [x] Flyway database migrations (schema-per-service)
- [x] Docker Compose infrastructure
- [x] React monorepo with shared API client
- [x] Admin Portal: Login, Dashboard (stat cards), Programs, Anchors, Loans pages

### 5.2 Phase 2 — Pay Day Loan
- [x] Employee salary data management (CSV upload + manual entry)
- [x] Earned salary calculation (grossSalary × daysWorked/totalWorkingDays)
- [x] Eligibility engine: checks overdue loans, concurrent loan limit, available borrower limit, salary eligibility
- [x] Loan request flow: borrower requests → system validates → creates REQUESTED loan
- [x] Admin approve/reject with sanctioned amount
- [x] Disbursement with limit blocking (Redis + DB)
- [x] Repayment recording (partial + full) with outstanding amount tracking
- [x] Loan closure on full repayment
- [x] Anchor Portal: Dashboard, Employees, Salary Upload (3 tabs: CSV, Manual, View)
- [x] Borrower Portal: Dashboard, Loan Request with eligibility check, My Loans

### 5.3 Phase 3 — Invoice Discounting
- [x] Invoice entity with three-way match (PO number, GRN number)
- [x] Invoice workflow: UPLOADED → VERIFIED → ELIGIBLE → PARTIALLY/FULLY_DISCOUNTED
- [x] Invoice CSV upload + manual entry + duplicate detection
- [x] Buyer verification and anchor confirmation
- [x] Invoice discounting eligibility engine (checks overdue, invoice status, borrower limits)
- [x] Discounting loan request linked to specific invoice
- [x] Invoice mark-discounted on disbursement (with pessimistic locking)
- [x] Anchor Portal: Invoice Upload page (3 tabs: CSV Upload, Manual, View with Verify/Confirm)
- [x] Borrower Portal: Invoice Discounting page (eligible invoices, eligibility check, request)

### 5.4 Phase 4 — Production Readiness
- [x] **RabbitMQ Event Publishing** — LoanEventPublisher fires events on every loan state change
- [x] **Audit Trail** — AuditEventConsumer persists audit records from RabbitMQ to plp_report.audit_trail
- [x] **Notifications** — LoanEventConsumer creates IN_APP notifications from loan events using templates
- [x] **5 Notification Templates** — LOAN_REQUESTED, LOAN_APPROVED, LOAN_DISBURSED, REPAYMENT_DUE, REPAYMENT_RECEIVED
- [x] **Reports** — Disbursement Summary, Portfolio Summary (by program), Overdue/DPD aging
- [x] **CSV Export** — All report types exportable as CSV
- [x] **KFS (Key Fact Statement)** — RBI-compliant HTML generation with APR, charges, cooling-off period
- [x] **Admin Portal** — Reports page (3 types + CSV), Notifications management, Audit Trail viewer
- [x] **Borrower Portal** — Notifications tab, Repayment History, KFS download
- [x] **Anchor Portal** — Settlement Tracking, Reports download

---

## 6. API Reference

### 6.1 Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Login (returns JWT accessToken) |
| POST | `/api/v1/auth/register` | Register new user |

**Default users:**
- Admin: `admin@plp.com` / `Admin@PLP2026`
- Borrower: `raj@testcorp.com` / `Raj@2026`

### 6.2 Programs
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/programs` | List all programs |
| POST | `/api/v1/programs` | Create program |
| GET | `/api/v1/programs/{id}` | Get program details |
| PUT | `/api/v1/programs/{id}` | Update program |

### 6.3 Anchors & Borrowers
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/anchors` | List anchors |
| POST | `/api/v1/anchors` | Create anchor |
| GET | `/api/v1/borrowers` | List borrowers |
| POST | `/api/v1/borrowers` | Create borrower |
| GET | `/api/v1/borrowers/{id}/limits` | Get borrower limits |
| POST | `/api/v1/borrowers/{id}/limits/block` | Block limit amount |
| POST | `/api/v1/borrowers/{id}/limits/release` | Release limit amount |

### 6.4 Salary Data
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/salary` | Create/update salary entry |
| POST | `/api/v1/salary/upload` | Upload salary CSV |
| GET | `/api/v1/salary?borrowerId=&payPeriod=` | Get salary records |

### 6.5 Invoices
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/invoices` | Create invoice |
| POST | `/api/v1/invoices/upload` | Upload invoice CSV |
| GET | `/api/v1/invoices?anchorId=&status=` | List invoices |
| POST | `/api/v1/invoices/{id}/verify` | Verify invoice |
| POST | `/api/v1/invoices/{id}/confirm` | Confirm invoice (mark eligible) |
| POST | `/api/v1/invoices/{id}/mark-discounted` | Mark as discounted |

### 6.6 Loans
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/loans` | List all loans |
| GET | `/api/v1/loans/{id}` | Get loan details |
| POST | `/api/v1/loans/{id}/approve` | Approve loan |
| POST | `/api/v1/loans/{id}/reject` | Reject loan |
| POST | `/api/v1/loans/{id}/disburse` | Disburse loan |
| POST | `/api/v1/loans/{id}/repay` | Record repayment |
| GET | `/api/v1/loans/{id}/kfs` | Generate Key Fact Statement |

### 6.7 Borrower Portal
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portal/borrower/eligibility` | Check PDL eligibility |
| POST | `/api/v1/portal/borrower/loans/request` | Request new loan |
| GET | `/api/v1/portal/borrower/loans?borrowerId=` | My loans |
| GET | `/api/v1/portal/borrower/invoices/eligible` | Eligible invoices for discounting |
| POST | `/api/v1/portal/borrower/invoices/check-eligibility` | Check ID eligibility |
| POST | `/api/v1/portal/borrower/invoices/request-discounting` | Request invoice discounting |

### 6.8 Reports & Audit
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/reports/disbursement-summary` | Disbursement summary report |
| GET | `/api/v1/reports/portfolio-summary` | Portfolio summary by program |
| GET | `/api/v1/reports/overdue` | Overdue/DPD aging report |
| GET | `/api/v1/reports/export/disbursement-summary` | CSV export |
| GET | `/api/v1/reports/audit` | Paginated audit trail |
| POST | `/api/v1/reports/audit` | Create audit entry |

### 6.9 Notifications
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/notifications?recipientId=` | Get notifications |
| GET | `/api/v1/notifications/unread-count?recipientId=` | Unread count |
| GET | `/api/v1/notifications/templates` | List templates |
| PUT | `/api/v1/notifications/templates/{id}` | Update template |
| POST | `/api/v1/notifications/send-direct` | Send direct notification |

---

## 7. Event Architecture (RabbitMQ)

### Exchanges & Queues
| Exchange | Queue | Purpose |
|----------|-------|---------|
| `plp.loan.events` | `plp.loan.events.notification` | Loan lifecycle → Notifications |
| `plp.audit.events` | `plp.audit.queue` | All actions → Audit Trail |

### Event Types Published
- `LOAN_REQUESTED` — when borrower requests a loan
- `LOAN_APPROVED` — when admin approves
- `LOAN_DISBURSED` — when funds are disbursed
- `REPAYMENT_RECEIVED` — when repayment is recorded
- Audit events for all above + `REJECTED`

---

## 8. Security

- **JWT Authentication** — HS512 signed tokens via API Gateway
- **Role-Based Access** — PLATFORM_ADMIN, ANCHOR_USER, BORROWER
- **Gateway Filter** — X-User-Id and X-User-Roles headers injected for downstream services
- **Pessimistic Locking** — `PESSIMISTIC_WRITE` on loan updates and invoice discounting to prevent race conditions
- **Limit Blocking** — Atomic limit reservation during disbursement with compensating release on failure

---

## 9. How to Run Locally

### Prerequisites
- Java 21 (OpenJDK)
- Maven 3.9+
- Node.js 18+ with npm
- Docker & Docker Compose

### Step 1: Start Infrastructure
```bash
docker compose -f docker-compose.infra.yml up -d
```
This starts PostgreSQL, Redis, RabbitMQ, and MinIO.

### Step 2: Build Backend
```bash
export JAVA_HOME=/path/to/java-21
mvn clean compile -DskipTests
```

### Step 3: Start Services (in order)
```bash
# Terminal 1 — Discovery Service
cd services/discovery-service && mvn spring-boot:run

# Terminal 2 — API Gateway (wait for discovery to start)
cd services/api-gateway && mvn spring-boot:run

# Terminal 3 — IAM Service
cd services/iam-service && mvn spring-boot:run

# Terminal 4 — Program Service
cd services/program-service && mvn spring-boot:run

# Terminal 5 — Lending Service
cd services/lending-service && mvn spring-boot:run

# Terminal 6 — Notification Service
cd services/notification-service && mvn spring-boot:run

# Terminal 7 — Report Service
cd services/report-service && mvn spring-boot:run
```

### Step 4: Start Frontend Portals
```bash
cd frontend && npm install

# Terminal A — Admin Portal
cd frontend/packages/admin-portal && npx vite --port 3000

# Terminal B — Anchor Portal
cd frontend/packages/anchor-portal && npx vite --port 3001

# Terminal C — Borrower Portal
cd frontend/packages/borrower-portal && npx vite --port 3002
```

### Step 5: Access
- **Eureka Dashboard**: http://localhost:8761
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **Admin Portal**: http://localhost:3000
- **Anchor Portal**: http://localhost:3001
- **Borrower Portal**: http://localhost:3002

---

## 10. Testing Flow (Quick Start)

### Pay Day Loan Flow
1. Login to Admin Portal → Create a Program (PAY_DAY_LOAN) → Create an Anchor → Create a Borrower with limits
2. Login to Anchor Portal → Upload salary data (CSV or manual) for the borrower
3. Login to Borrower Portal → Check eligibility → Request a PDL loan
4. Login to Admin Portal → Approve the loan → Disburse
5. Login to Borrower Portal → Make repayment(s) → Loan closes when fully repaid
6. Check Admin Portal → Reports, Audit Trail, Notifications

### Invoice Discounting Flow
1. Create a Program (INVOICE_DISCOUNTING) with anchor and borrower
2. Anchor Portal → Upload invoice → Verify → Confirm (marks ELIGIBLE)
3. Borrower Portal → Invoice Discounting → Check eligibility → Request discounting
4. Admin → Approve → Disburse (invoice automatically marked as discounted)
5. Borrower → Repay

---

## 11. Codebase Statistics

| Metric | Count |
|--------|-------|
| Java source files | 103 |
| TypeScript/TSX files | 36 |
| SQL migration files | 12 |
| YAML config files | 9 |
| Java lines of code | ~5,900 |
| TypeScript lines of code | ~4,400 |
| Total lines of code | ~10,300 |
| Backend services | 8 |
| Frontend portals | 3 |
| Database schemas | 6 |
| API endpoints | 40+ |
| RabbitMQ queues | 2 |
| Notification templates | 5 |

---

## 12. Key Design Decisions

1. **Schema-per-service** — Each microservice owns its PostgreSQL schema, ensuring data isolation
2. **Eureka for discovery** — Services register and discover each other dynamically
3. **Redis for limits** — Sub-millisecond limit checks for STP (Straight-Through Processing)
4. **RabbitMQ for async** — Notifications and audit trail are event-driven, non-blocking
5. **Adapter pattern** — Integration service uses pluggable adapters for HR/ERP/Payment systems
6. **GenerationType.UUID** — Hibernate 6.x native UUID generation (no database sequences needed)
7. **Pessimistic locking** — Prevents concurrent over-discounting on invoices and double-spending on limits
8. **Compensating transactions** — Disbursement releases blocked limits if downstream calls fail
9. **IN_APP notifications** — Default channel (switch to EMAIL when SMTP is configured)
10. **KFS compliance** — Key Fact Statement follows RBI Digital Lending Directions 2025

---

## 13. Future Enhancements (Not Yet Built)

- [ ] LoS API integration (KYC, bureau checks, credit scoring)
- [ ] Real HR system adapter (ADP, Darwinbox, etc.)
- [ ] Real ERP system adapter (SAP, Tally, etc.)
- [ ] Payment gateway integration (NEFT/RTGS/IMPS disbursement)
- [ ] Email/SMS/WhatsApp notification delivery
- [ ] Auto-approval engine for STP
- [ ] DPD/NPA marking scheduler
- [ ] Penal interest calculation
- [ ] Multi-lender support
- [ ] Kubernetes deployment manifests
- [ ] API rate limiting and throttling
- [ ] Comprehensive unit and integration tests
