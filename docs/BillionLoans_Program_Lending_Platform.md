# BillionLoans Program Lending Platform (PLP)

## 🧭 Overview

The **Program Lending Platform (PLP)** is a full-stack, multi-tenant digital lending system designed for:

* **Supply Chain Finance (Invoice Discounting)**
* **Pay Day Loans (Employee Financing)**

It enables seamless interaction between:

* **Lender (NBFC / Bank)**
* **Anchor (Corporate / Employer / Buyer / Seller)**
* **Borrower (Distributor / Employee / Vendor)**

---

## 🏗️ Core Business Flows

### 1. Invoice Discounting

| Role     | Entity                                      |
| -------- | ------------------------------------------- |
| Anchor   | Seller (Purchase Bill) / Buyer (Sales Bill) |
| Borrower | Buyer / Seller                              |

**Flow:**

1. Invoice uploaded (Anchor)
2. Borrower accepts invoice
3. Borrower requests financing
4. Lender sanctions loan
5. Disbursement
6. Repayment

---

### 2. Pay Day Loan

| Role     | Entity   |
| -------- | -------- |
| Anchor   | Employer |
| Borrower | Employee |

**Flow:**

1. Salary slip uploaded
2. Borrower requests loan
3. Eligibility check (salary + limits)
4. Loan sanction
5. Disbursement
6. Repayment

---

## 🧱 Core Data Model

### Program → Sub Program → Borrower

```
Program (Umbrella)
   ↓
Sub Program (Anchor + Limits)
   ↓
Borrower (Linked to multiple sub-programs)
```

### Key Concepts

* **Program**: Logical grouping (no anchor dependency)
* **Sub Program**: Actual lending configuration

  * Anchor linked
  * Limits defined
* **Borrower**: Can be linked to multiple sub-programs

---

## 🔐 Multi-Tenant Security Model

Each user has:

```json
{
  "linkedEntityId": "...",
  "linkedEntityType": "ANCHOR | BORROWER | LENDER"
}
```

Access is enforced via:

* API Gateway headers
* Service-level guards
* RBAC roles

---

## 👥 Roles

### Lender

* PLATFORM_ADMIN
* CREDIT_ANALYST
* CREDIT_MANAGER
* ACCOUNTS_OFFICER
* ACCOUNTS_MANAGER
* COMPLIANCE_OFFICER

### Anchor

* ANCHOR_ADMIN
* ANCHOR_MAKER
* ANCHOR_CHECKER

### Borrower

* BORROWER

---

## ⚙️ Technical Architecture
Core Infrastructure Services
| Service                        | Purpose                                      |
| ------------------------------ | -------------------------------------------- |
| **discovery-service (Eureka)** | Service registry & discovery                 |
| **api-gateway**                | Entry point, routing, auth headers injection |



### Business Microservices

| Service                  | Purpose                                                      |
| ------------------------ | ------------------------------------------------------------ |
| **iam-service**          | Authentication, JWT, user management                         |
| **program-service**      | Programs, sub-programs, anchors, borrowers, invoices, salary |
| **lending-service**      | Loans, eligibility, disbursement, repayment                  |
| **integration-service**  | External integrations                                        |
| **notification-service** | Email/SMS                                                    |
| **report-service**       | Reporting                                                    |


Service Communication
Services do NOT call each other directly via fixed URLs
They use service names:
http://program-service
http://lending-service
Eureka resolves actual instance

Service Registration Flow
Each service on startup:
   ↓
Registers with discovery-service (Eureka)
   ↓
Eureka maintains service registry


Request Flow
Frontend → API Gateway
           ↓
     Gateway resolves service via Eureka
           ↓
     Routes request to correct service


Example
GET /api/v1/loans
   ↓
api-gateway
   ↓
Eureka lookup → lending-service
   ↓
lending-service handles request





---

### Frontend Applications

| App                 | Purpose         |
| ------------------- | --------------- |
| **platform-ui**     | Lender Portal   |
| **anchor-portal**   | Anchor Portal   |
| **borrower-portal** | Borrower Portal |

---

## 🔄 Loan Lifecycle

```
REQUESTED
   ↓
SANCTIONED
   ↓
DISBURSEMENT_PENDING
   ↓         ↓
DISBURSED   CANCELLED
   ↓
REPAYMENT_DUE
   ↓
CLOSED
```

---

## 💰 Limit Management

Limits are enforced at:

### 1. Sub-Program Level (Pool)

### 2. Borrower Level

Final available:

```
effectiveAvailable = min(subProgramAvailable, borrowerAvailable)
```

---

## 🧠 Key Features

* Multi-tenant architecture
* Strong RBAC enforcement
* Real-time limit validation
* Invoice lifecycle tracking
* Salary-based lending
* Audit logs for all actions
* Maker-checker workflows
* Digital document storage (MinIO)
* Automated eligibility engine

---

## 🔍 Audit System

Tracks:

* Loan events
* Program changes
* Access denied attempts

Supports:

* Timeline view
* Filtering
* Entity-level tracking

---

## 🧰 Dev Utilities

* Reset demo data APIs
* Repair endpoints for legacy loans
* Debug logging for limits

---

## 🚀 Summary

PLP is a **production-grade fintech lending system** that supports:

* Embedded finance
* Supply chain lending
* Employee financing
* Multi-tenant enterprise workflows

Built with:

* **Spring Boot microservices**
* **React (Vite) frontends**
* **PostgreSQL**
* **Redis / RabbitMQ (optional)**
* **Docker-ready architecture**

---
