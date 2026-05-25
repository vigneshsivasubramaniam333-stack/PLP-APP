# Business Requirements Document (BRD)

## Program Lending Platform — Pay Day Loan & Invoice Discounting (Purchase Bill Discounting)

**Version:** 1.0  
**Date:** May 2026  
**Author:** BillionTech Engineering  
**Status:** Draft for Review  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Business Context & Objectives](#2-business-context--objectives)
3. [Scope](#3-scope)
4. [Stakeholders & Personas](#4-stakeholders--personas)
5. [Product Definitions](#5-product-definitions)
6. [Program Concept & Hierarchy](#6-program-concept--hierarchy)
7. [Functional Requirements — Pay Day Loan](#7-functional-requirements--pay-day-loan)
8. [Functional Requirements — Invoice Discounting (Purchase Bill Discounting)](#8-functional-requirements--invoice-discounting-purchase-bill-discounting)
9. [Common Functional Requirements](#9-common-functional-requirements)
10. [Integration Requirements](#10-integration-requirements)
11. [Regulatory & Compliance Requirements](#11-regulatory--compliance-requirements)
12. [Non-Functional Requirements](#12-non-functional-requirements)
13. [Business Rules & Parameters](#13-business-rules--parameters)
14. [Reporting & MIS Requirements](#14-reporting--mis-requirements)
15. [User Interface Requirements](#15-user-interface-requirements)
16. [Acceptance Criteria](#16-acceptance-criteria)
17. [Assumptions & Constraints](#17-assumptions--constraints)
18. [Glossary](#18-glossary)

---

## 1. Executive Summary

This document defines the business requirements for a **standalone Program Lending Platform** that supports two new lending products:

1. **Pay Day Loan (Earned Wage Access / Salary Advance)** — Short-term loans to employees against their accumulated/earned salary, facilitated through employer programs.
2. **Invoice Discounting (Purchase Bill Discounting)** — Short-term financing against purchase invoices raised by sellers, enabling buyers (borrowers) to get early payment financing.

Both products operate on a **Program model** with three key participants:
- **Anchor** — The entity that enables the lending program (Employer for Pay Day, Seller for Invoice Discounting)
- **Borrower** — The entity taking the loan (Employee for Pay Day, Purchaser/Buyer for Invoice Discounting)
- **Lender** — The financial institution providing the funds (Bank/NBFC)

The platform is designed as an independent application with its own tech stack, database, and UI, while exposing APIs for future integration with BillionTech LoS and external systems.

---

## 2. Business Context & Objectives

### 2.1 Business Context

The Indian lending landscape has seen rapid growth in short-tenure, program-based lending:
- **Earned Wage Access (EWA)** market in India is growing at ~50% CAGR with players like Jify, Refyne, EarlySalary
- **Invoice/Bill Discounting** is a ₹10+ lakh crore market with TReDS platforms and NBFC-driven supply chain finance programs
- RBI's Digital Lending Directions (2025) provide clear regulatory framework for such products
- Both products share structural similarities: anchor-borrower-lender triangle, program-level parameters, short tenure, and high volume

### 2.2 Business Objectives

| # | Objective | Success Metric |
|---|-----------|----------------|
| BO-1 | Launch Pay Day Loan product within 90 days | First loan disbursed |
| BO-2 | Launch Invoice Discounting within 90 days | First invoice discounted |
| BO-3 | Onboard 10+ anchor programs in Year 1 | Active program count |
| BO-4 | Process 10,000+ loan requests/month | Monthly disbursement volume |
| BO-5 | Maintain NPA < 2% through program guardrails | Portfolio quality |
| BO-6 | Achieve 90%+ straight-through processing | STP rate |
| BO-7 | Full RBI compliance (Digital Lending Directions 2025) | Audit clearance |

### 2.3 Strategic Fit

This platform complements the existing BillionTech LoS by addressing:
- **High-volume, low-ticket lending** not served by traditional loan origination
- **Program-based lending** with anchor-level risk mitigation
- **Short-tenure products** (7–90 days) requiring rapid processing
- **API-first integration** with external systems (HR, ERP, LoS)

---

## 3. Scope

### 3.1 In Scope

| Area | Description |
|------|-------------|
| Program Management | Create, configure, and manage lending programs with anchor-borrower-lender combinations |
| Anchor Onboarding | KYC, agreement, limit setup for anchors |
| Borrower Onboarding | KYC, eligibility, limit assignment within programs |
| Pay Day Loan Origination | Salary-based loan request, eligibility check, approval, disbursement |
| Invoice Discounting Origination | Invoice upload, validation, discounting request, approval, disbursement |
| Repayment Management | Auto-debit, manual repayment, settlement on due date |
| Limit Management | Program-level, anchor-level, and borrower-level limits |
| Integration Layer | APIs for HR systems, ERP systems, BillionTech LoS, payment gateways |
| Reporting & MIS | Operational, regulatory, and analytical reports |
| Admin & Configuration | Product parameters, workflow rules, user management |

### 3.2 Out of Scope (Phase 1)

| Area | Rationale |
|------|-----------|
| Collections & Recovery | Will integrate with existing LoS/LMS capabilities |
| Credit Bureau Integration | Will use BillionTech LoS credit decisioning APIs |
| Full KYC (Video KYC, DigiLocker) | Will leverage BillionTech LoS enrollment service |
| Co-Lending | Future phase — can reuse LoS co-lending module |
| TReDS Integration | Future phase — requires separate PSO license |
| Mobile App | Phase 2 — React Native or Flutter |

---

## 4. Stakeholders & Personas

### 4.1 Primary Stakeholders

| Stakeholder | Role | Interests |
|-------------|------|-----------|
| Lender (Platform Operator) | Owns the platform, provides funds | Portfolio growth, risk management, compliance |
| Anchor (Employer / Seller) | Enables programs for their ecosystem | Employee retention / Vendor relationships, working capital optimization |
| Borrower (Employee / Buyer) | Takes loans | Quick access to funds, transparent charges |
| Regulator (RBI) | Oversight | Consumer protection, systemic stability |

### 4.2 User Personas

| Persona | Description | Access Level |
|---------|-------------|--------------|
| **Platform Admin** | Manages system configuration, products, users | Full system access |
| **Program Manager** | Creates and manages lending programs, anchor relationships | Program CRUD, reports |
| **Credit Analyst** | Reviews applications requiring manual intervention | Application review, limit decisions |
| **Anchor Admin** | Employer/Seller representative managing their program | View borrowers, upload data, view utilization |
| **Borrower** | Employee/Buyer requesting loans | Self-service portal — request loan, view status, repay |
| **Finance/Treasury** | Manages fund allocation, reconciliation | Disbursement, reconciliation reports |
| **Compliance Officer** | Ensures regulatory compliance | Audit logs, compliance reports |

---

## 5. Product Definitions

### 5.1 Pay Day Loan (Earned Wage Access)

| Attribute | Details |
|-----------|---------|
| **Product Name** | Pay Day Loan / Earned Wage Access |
| **Description** | Short-term loan to an employee against salary earned/accumulated in the current pay cycle |
| **Borrower** | Employee of a registered Anchor (Employer) |
| **Anchor** | Employer — provides salary data, guarantees repayment via salary deduction |
| **Lender** | Bank / NBFC operating the platform |
| **Loan Amount** | Up to 50–80% of net salary earned/accumulated till date |
| **Tenure** | 7 to 60 days (typically until next salary date) |
| **Interest Rate** | 12–24% p.a. (flat or reducing), or flat fee per withdrawal |
| **Processing Fee** | 0–2% of loan amount or ₹0 (built into interest) |
| **Repayment** | Bullet repayment on salary date via salary deduction or NACH mandate |
| **Collateral** | Unsecured — employer's salary flow acts as comfort |
| **Eligibility** | Minimum employment tenure, salary threshold, no existing overdue |
| **Data Source** | HR/Payroll system — attendance, salary components, leave data |
| **Disbursement** | Instant to borrower's salary account via IMPS/UPI/NEFT |
| **Key Risk Mitigation** | Employer undertaking, salary deduction authorization, program limits |

### 5.2 Invoice Discounting (Purchase Bill Discounting)

| Attribute | Details |
|-----------|---------|
| **Product Name** | Invoice Discounting / Purchase Bill Discounting |
| **Description** | Short-term financing to a buyer (purchaser) against invoices raised by the seller |
| **Borrower** | Purchaser/Buyer — entity receiving goods/services |
| **Anchor** | Seller — entity raising invoices and enabling the program |
| **Lender** | Bank / NBFC operating the platform |
| **Loan Amount** | Up to 80–90% of invoice value (after margin) |
| **Tenure** | 30 to 180 days (aligned with invoice payment terms) |
| **Interest/Discount Rate** | 10–18% p.a. (varies by anchor rating, tenor) |
| **Processing Fee** | 0.1–0.5% per transaction |
| **Repayment** | Bullet on invoice due date or EMI for longer tenures |
| **Collateral** | Invoice/receivable acts as underlying asset; may require additional collateral above limits |
| **Eligibility** | Anchor-approved borrower, within sanctioned limit, invoice verified |
| **Data Source** | ERP/Accounting system — invoices, purchase orders, GRN, payment history |
| **Disbursement** | To seller's account (or buyer's account per program structure) |
| **Key Risk Mitigation** | Invoice verification, anchor confirmation, three-way match (PO-GRN-Invoice), dilution reserves |

---

## 6. Program Concept & Hierarchy

### 6.1 Program Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PROGRAM                                      │
│  (Unique combination: Anchor + Lender + Product Type)               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  PROGRAM-LEVEL PARAMETERS                                   │     │
│  │  • Program Limit (max total exposure)                       │     │
│  │  • Anchor Limit (max exposure to this anchor)               │     │
│  │  • Interest Rate / Discount Rate range                      │     │
│  │  • Default tenure / Max tenure                              │     │
│  │  • Product type (PAY_DAY / INVOICE_DISCOUNTING)             │     │
│  │  • Eligibility criteria templates                           │     │
│  │  • Auto-approval rules & thresholds                         │     │
│  │  • Document requirements                                    │     │
│  │  • Fee structure                                            │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  BORROWER-LEVEL PARAMETERS (within this program)            │     │
│  │  • Individual borrower limit                                │     │
│  │  • Utilized amount / Available amount                       │     │
│  │  • Borrower-specific interest rate (override)               │     │
│  │  • Eligibility status & last evaluated date                 │     │
│  │  • Number of active loans                                   │     │
│  │  • Repayment track record score                             │     │
│  │  • Max concurrent loans allowed                             │     │
│  │  • Cooling-off period between loans                         │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Program Hierarchy — Pay Day Loan

```
Lender (NBFC/Bank)
  └── Program: "Employer X Pay Day Program"
        ├── Anchor: Employer X
        │     ├── Program Limit: ₹5 Crore
        │     ├── Max per-employee limit: ₹2 Lakh
        │     ├── Interest: 18% p.a.
        │     ├── Max tenure: 45 days
        │     └── Eligibility: Min 6 months employment, min salary ₹25K
        │
        └── Borrowers (Employees of Employer X)
              ├── Employee A: Limit ₹80,000 | Utilized ₹30,000
              ├── Employee B: Limit ₹1,20,000 | Utilized ₹0
              └── Employee C: Limit ₹50,000 | Utilized ₹50,000 (fully utilized)
```

### 6.3 Program Hierarchy — Invoice Discounting

```
Lender (NBFC/Bank)
  └── Program: "Seller Y Purchase Bill Discounting"
        ├── Anchor: Seller Y (who raises invoices)
        │     ├── Program Limit: ₹50 Crore
        │     ├── Max per-borrower limit: ₹5 Crore
        │     ├── Discount Rate: 14% p.a.
        │     ├── Max tenure: 120 days
        │     └── Margin: 10% (finance up to 90% of invoice)
        │
        └── Borrowers (Buyers/Purchasers from Seller Y)
              ├── Buyer P: Limit ₹3 Crore | Utilized ₹1.5 Crore
              ├── Buyer Q: Limit ₹2 Crore | Utilized ₹80 Lakh
              └── Buyer R: Limit ₹5 Crore | Utilized ₹0
```

### 6.4 Multi-Program Support

A single anchor can have multiple programs (with different lenders or different parameters):
- Employer X may have Pay Day programs with Lender A (₹5 Cr) and Lender B (₹3 Cr)
- Seller Y may have Invoice Discounting programs with Lender A and Lender C

A single borrower can participate in multiple programs:
- Employee working for Employer X can borrow from either program (subject to limits)
- Buyer purchasing from multiple sellers can have limits under each seller's program

---

## 7. Functional Requirements — Pay Day Loan

### 7.1 Employer (Anchor) Onboarding

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-AO-01 | System shall allow onboarding of employers as anchors with: company details, GST, PAN, CIN, registered address, authorized signatories | Must |
| PDL-AO-02 | System shall capture employer's HR/payroll system details for integration (system type, API endpoints, authentication) | Must |
| PDL-AO-03 | System shall support execution and storage of Master Agreement between Lender and Employer | Must |
| PDL-AO-04 | System shall allow configuration of employer-level program parameters (limits, rates, eligibility criteria) | Must |
| PDL-AO-05 | System shall perform basic due diligence: CIN verification, GST validation, bank account verification | Must |
| PDL-AO-06 | System shall capture employer's undertaking for salary deduction authorization | Must |
| PDL-AO-07 | System shall support anchor status lifecycle: DRAFT → UNDER_REVIEW → ACTIVE → SUSPENDED → TERMINATED | Must |

### 7.2 Employee (Borrower) Onboarding

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-BO-01 | System shall allow employees to self-register via employer-provided link/code | Must |
| PDL-BO-02 | System shall perform e-KYC: PAN verification, Aadhaar-based (OTP or biometric), bank account validation | Must |
| PDL-BO-03 | System shall fetch employee details from HR system: employee ID, designation, department, date of joining, salary components | Must |
| PDL-BO-04 | System shall calculate individual borrower limit based on: net salary, employment tenure, program parameters | Must |
| PDL-BO-05 | System shall obtain digital consent from employee for: salary deduction, data sharing, NACH mandate | Must |
| PDL-BO-06 | System shall register NACH/e-NACH mandate for automated repayment on salary date | Should |
| PDL-BO-07 | System shall maintain borrower status: PENDING_KYC → ACTIVE → SUSPENDED → BLACKLISTED | Must |
| PDL-BO-08 | System shall re-evaluate borrower eligibility monthly based on updated salary data | Must |

### 7.3 Salary Data Integration

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-SD-01 | System shall integrate with employer's HR/Payroll system to fetch real-time or daily salary accrual data | Must |
| PDL-SD-02 | System shall support multiple HR system types: SAP SuccessFactors, Oracle HCM, Darwinbox, greytHR, Keka, custom APIs | Should |
| PDL-SD-03 | System shall compute "Earned Salary" as: (Net Monthly Salary / Total Working Days) × Days Worked in Current Cycle | Must |
| PDL-SD-04 | System shall account for: leave without pay (LWP), loss of pay (LOP), overtime, variable pay (only guaranteed portion) | Must |
| PDL-SD-05 | System shall refresh salary accrual data at least once daily (configurable frequency) | Must |
| PDL-SD-06 | System shall handle salary revisions mid-cycle (use new salary from effective date) | Should |
| PDL-SD-07 | System shall support fallback mechanism: if real-time HR integration fails, use last known salary with a conservative multiplier (e.g., 80%) | Must |
| PDL-SD-08 | System shall detect employment termination/resignation events and freeze borrower account immediately | Must |

### 7.4 Loan Request & Processing

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-LR-01 | Borrower shall be able to request loan amount up to eligible limit (% of earned salary, minus any outstanding) | Must |
| PDL-LR-02 | System shall display: eligible amount, interest/fee applicable, repayment date, total payable — before borrower confirms | Must |
| PDL-LR-03 | System shall perform real-time eligibility check: earned salary available, within borrower limit, within program limit, no existing overdue | Must |
| PDL-LR-04 | System shall auto-approve requests within configured threshold (e.g., < ₹50,000 or < 50% of limit) | Must |
| PDL-LR-05 | System shall route requests exceeding threshold for manual review by Credit Analyst | Must |
| PDL-LR-06 | System shall generate Key Fact Statement (KFS) as per RBI Digital Lending Directions 2025 | Must |
| PDL-LR-07 | System shall obtain explicit borrower consent on KFS before proceeding | Must |
| PDL-LR-08 | System shall provide a cooling-off period (minimum 3 days for loans > 7 days tenure) as per RBI guidelines | Must |
| PDL-LR-09 | System shall support loan cancellation within cooling-off period with full refund | Must |
| PDL-LR-10 | System shall process approved loans for disbursement within 15 minutes (STP) or 4 hours (manual review) | Must |

### 7.5 Disbursement

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-DB-01 | System shall disburse loan amount directly to borrower's verified bank account (same account as salary credit) | Must |
| PDL-DB-02 | Disbursement shall be via IMPS (instant, up to ₹5L), UPI (up to ₹2L), or NEFT/RTGS (higher amounts) | Must |
| PDL-DB-03 | System shall NOT disburse to any third-party account (RBI mandate — only to borrower's account) | Must |
| PDL-DB-04 | System shall record: UTR number, disbursement timestamp, mode of transfer, bank response | Must |
| PDL-DB-05 | System shall send disbursement confirmation to borrower via SMS/Email/WhatsApp | Must |
| PDL-DB-06 | System shall handle failed disbursements: auto-retry (2 attempts), then flag for manual resolution | Must |

### 7.6 Repayment & Settlement

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| PDL-RP-01 | Primary repayment: Salary deduction by employer on next salary date, remitted to lender | Must |
| PDL-RP-02 | Secondary repayment: NACH/e-NACH auto-debit from borrower's bank account (if salary deduction fails) | Must |
| PDL-RP-03 | Tertiary repayment: Borrower can voluntarily prepay via UPI/NEFT/Net Banking at any time | Should |
| PDL-RP-04 | System shall calculate: principal + interest (pro-rata for actual days) + any applicable fees | Must |
| PDL-RP-05 | System shall not charge prepayment penalty for loans < ₹50,000 tenure < 60 days (market practice) | Should |
| PDL-RP-06 | System shall handle partial salary scenarios: if salary < repayment amount, carry forward balance with additional interest | Must |
| PDL-RP-07 | System shall mark loans overdue if not repaid within T+3 of salary date | Must |
| PDL-RP-08 | System shall notify borrower and employer of overdue status | Must |
| PDL-RP-09 | System shall freeze borrower's new loan eligibility on any overdue | Must |
| PDL-RP-10 | System shall update borrower's available limit immediately upon full repayment | Must |

---

## 8. Functional Requirements — Invoice Discounting (Purchase Bill Discounting)

### 8.1 Seller (Anchor) Onboarding

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-AO-01 | System shall allow onboarding of sellers as anchors with: entity details, GST, PAN, CIN, MSME registration (if applicable) | Must |
| ID-AO-02 | System shall capture seller's ERP/Accounting system details for invoice data integration | Must |
| ID-AO-03 | System shall support Master Agreement between Lender and Seller (covering invoice verification responsibilities) | Must |
| ID-AO-04 | System shall configure anchor-level parameters: program limit, max per-buyer limit, discount rate range, acceptable invoice age, margin % | Must |
| ID-AO-05 | System shall verify seller's entity: GST verification, CIN check, bank account validation, trade references | Must |
| ID-AO-06 | System shall capture seller's undertaking for invoice authenticity and non-duplication | Must |
| ID-AO-07 | System shall support anchor rating (internal): A/B/C based on track record, financials, vintage | Should |

### 8.2 Buyer (Borrower) Onboarding

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-BO-01 | System shall allow onboarding of buyers as borrowers, linked to specific seller (anchor) programs | Must |
| ID-BO-02 | System shall perform buyer KYC: PAN, GST, CIN verification, bank account validation | Must |
| ID-BO-03 | System shall capture buyer's trade relationship with seller: historical purchase volume, payment track record | Must |
| ID-BO-04 | System shall assign buyer-level limit within the program based on: purchase history, creditworthiness, anchor recommendation | Must |
| ID-BO-05 | System shall obtain buyer's consent for: loan agreement, auto-debit, data sharing | Must |
| ID-BO-06 | System shall register NACH/e-NACH mandate for repayment on invoice due date | Should |
| ID-BO-07 | System shall maintain buyer status: PENDING_KYC → ACTIVE → SUSPENDED → BLACKLISTED | Must |
| ID-BO-08 | System shall support buyer participating in multiple programs (with different sellers/anchors) | Must |

### 8.3 Invoice Data Integration

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-ID-01 | System shall integrate with seller's ERP system to fetch invoice data: invoice number, date, amount, buyer details, payment terms, due date | Must |
| ID-ID-02 | System shall support multiple ERP systems: SAP, Oracle ERP, Tally, Zoho Books, custom APIs | Should |
| ID-ID-03 | System shall support manual invoice upload (bulk CSV/Excel, single entry) as fallback | Must |
| ID-ID-04 | System shall validate invoice: GST compliance, no duplicate invoice number, within program limits, buyer is registered | Must |
| ID-ID-05 | System shall perform three-way match where possible: Purchase Order → Goods Receipt Note (GRN) → Invoice | Should |
| ID-ID-06 | System shall support seller confirmation/acceptance of uploaded invoices (for buyer-initiated flows) | Must |
| ID-ID-07 | System shall detect and prevent invoice financing of already-discounted invoices (duplicate financing prevention) | Must |
| ID-ID-08 | System shall support credit notes and debit notes that adjust outstanding invoices | Should |

### 8.4 Discounting Request & Processing

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-DR-01 | Borrower (buyer) shall be able to request discounting against eligible invoices | Must |
| ID-DR-02 | System shall display: eligible invoices, discountable amount (invoice value minus margin), discount charges, net disbursement amount, repayment date | Must |
| ID-DR-03 | System shall calculate eligible amount: Invoice Value × (1 - Margin%) subject to borrower's available limit | Must |
| ID-DR-04 | System shall auto-approve discounting requests within configured thresholds (e.g., invoice < ₹10L, buyer within limit, anchor confirmed) | Must |
| ID-DR-05 | System shall route requests exceeding threshold for manual review | Must |
| ID-DR-06 | System shall generate KFS as per RBI Digital Lending Directions 2025 | Must |
| ID-DR-07 | System shall support batch discounting: multiple invoices in a single request | Should |
| ID-DR-08 | System shall calculate discount/interest on per-day basis from disbursement to invoice due date | Must |
| ID-DR-09 | System shall support cooling-off period as per RBI norms | Must |
| ID-DR-10 | System shall handle partial discounting: discount only a portion of invoice value | Should |

### 8.5 Disbursement

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-DB-01 | System shall disburse discounted amount to borrower's (buyer's) verified bank account | Must |
| ID-DB-02 | System shall support alternate flow: disbursement to seller's account (reverse factoring model) — configurable per program | Should |
| ID-DB-03 | Disbursement via NEFT/RTGS (same day for requests before cut-off) or IMPS (instant for < ₹5L) | Must |
| ID-DB-04 | System shall deduct upfront charges (processing fee, stamp duty if applicable) from disbursement amount | Must |
| ID-DB-05 | System shall record complete disbursement audit trail: UTR, timestamp, mode, amount, bank response | Must |
| ID-DB-06 | System shall send disbursement confirmation to borrower and anchor | Must |

### 8.6 Repayment & Settlement

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| ID-RP-01 | Primary repayment: Buyer pays on invoice due date (via designated collection account or NACH auto-debit) | Must |
| ID-RP-02 | System shall calculate total repayment: Principal + Discount/Interest (for actual days) + Fees | Must |
| ID-RP-03 | System shall support early repayment with pro-rata interest benefit | Should |
| ID-RP-04 | System shall handle overdue invoices: T+3 grace period, then penal interest applies | Must |
| ID-RP-05 | System shall notify borrower (buyer) and anchor (seller) of upcoming due dates (T-7, T-3, T-1) | Must |
| ID-RP-06 | System shall track settlement against specific invoices (not just pool-level) | Must |
| ID-RP-07 | System shall handle partial payments: apply to oldest outstanding first (FIFO) | Must |
| ID-RP-08 | System shall trigger anchor (seller) notification for overdue buyers — seller may assist in follow-up | Must |
| ID-RP-09 | System shall update borrower's available limit immediately upon full repayment of a transaction | Must |
| ID-RP-10 | System shall support buyer disputes: if buyer disputes invoice, loan may be recalled from anchor (per agreement terms) | Should |

---

## 9. Common Functional Requirements

### 9.1 Program Management

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| CMN-PM-01 | System shall support CRUD operations on Programs with status lifecycle: DRAFT → ACTIVE → PAUSED → CLOSED | Must |
| CMN-PM-02 | System shall enforce program-level limit utilization tracking in real-time | Must |
| CMN-PM-03 | System shall support program renewal/extension with updated parameters | Must |
| CMN-PM-04 | System shall maintain complete program change audit trail | Must |
| CMN-PM-05 | System shall support multiple programs per anchor (different lenders, different parameters) | Must |
| CMN-PM-06 | System shall allow program-level parameter modification without affecting existing active loans | Must |
| CMN-PM-07 | System shall auto-pause program if utilization hits 90% threshold (configurable) | Should |
| CMN-PM-08 | System shall support program validity period (start date, end date, auto-renewal flag) | Must |

### 9.2 Limit Management

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| CMN-LM-01 | System shall maintain hierarchical limits: Program → Anchor → Borrower | Must |
| CMN-LM-02 | System shall enforce: Borrower limit ≤ Anchor limit; Sum of all borrower utilizations ≤ Program limit | Must |
| CMN-LM-03 | System shall track: Sanctioned Limit, Utilized Limit, Available Limit, Overdue Amount | Must |
| CMN-LM-04 | System shall update available limits in real-time on disbursement and repayment events | Must |
| CMN-LM-05 | System shall support limit enhancement/reduction with maker-checker approval | Must |
| CMN-LM-06 | System shall freeze borrower limit on: KYC expiry, employment termination, overdue > threshold | Must |
| CMN-LM-07 | System shall support temporary limit increase (with validity period) | Should |
| CMN-LM-08 | System shall recalculate borrower limits periodically based on updated data (salary changes, purchase volume changes) | Must |

### 9.3 Interest & Fee Calculation

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| CMN-IC-01 | System shall support multiple interest calculation methods: flat rate, reducing balance, discount rate (upfront deduction) | Must |
| CMN-IC-02 | System shall calculate interest on actual/365 day-count basis | Must |
| CMN-IC-03 | System shall support interest rate override at borrower level (within program band) | Should |
| CMN-IC-04 | System shall apply penal interest on overdue: configurable rate, calculated from due date | Must |
| CMN-IC-05 | System shall support fee structures: processing fee (% or flat), documentation charges, stamp duty, GST on fees | Must |
| CMN-IC-06 | System shall compute Annual Percentage Rate (APR) for KFS disclosure | Must |
| CMN-IC-07 | System shall support fee waiver workflow (maker-checker) | Should |

### 9.4 Notifications & Communication

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| CMN-NT-01 | System shall send notifications via SMS, Email, WhatsApp (configurable per event type) | Must |
| CMN-NT-02 | Notification events: Loan request received, Approved, Disbursed, Repayment reminder, Overdue, Limit change | Must |
| CMN-NT-03 | System shall send KFS document to borrower's registered email | Must |
| CMN-NT-04 | System shall support notification templates configurable by product and program | Should |
| CMN-NT-05 | System shall maintain delivery status and retry failed notifications | Must |

### 9.5 Document Management

| Req ID | Requirement | Priority |
|--------|-------------|----------|
| CMN-DM-01 | System shall generate and store: Loan Agreement, KFS, Sanction Letter, NOC (on closure) | Must |
| CMN-DM-02 | System shall support e-Sign (Aadhaar-based or DSC) for loan agreement | Must |
| CMN-DM-03 | System shall store uploaded documents: invoices, POs, GRNs, KYC documents | Must |
| CMN-DM-04 | System shall support document versioning and immutable audit trail | Must |
| CMN-DM-05 | System shall generate monthly account statements for borrowers | Should |

---

## 10. Integration Requirements

### 10.1 External System Integrations

| Integration | Direction | Purpose | Protocol |
|-------------|-----------|---------|----------|
| HR/Payroll Systems (SAP SF, Darwinbox, greytHR, Keka) | Inbound | Salary data, attendance, employment status | REST API / SFTP |
| ERP Systems (SAP, Oracle, Tally, Zoho) | Inbound | Invoice data, PO, GRN | REST API / SFTP |
| Payment Gateway (Razorpay/Cashfree) | Outbound | Disbursement, collections | REST API |
| NACH/e-NACH (NPCI) | Outbound | Mandate registration, auto-debit | API + File-based |
| BillionTech LoS | Bidirectional | KYC verification, credit bureau, notifications | REST API |
| GST Verification (NIC/GSP) | Outbound | GST validation for anchors/borrowers | REST API |
| PAN Verification (NSDL/UIDAI) | Outbound | PAN validation | REST API |
| Bank Account Verification (Penny drop) | Outbound | Verify bank account ownership | REST API |
| e-Sign Provider (Leegality/eMsigner) | Outbound | Digital loan agreement signing | REST API |
| Credit Bureau (CIBIL/Equifax) | Outbound (via LoS) | Bureau pull for higher-ticket loans | REST API |
| SMS/Email/WhatsApp Provider | Outbound | Notifications | REST API |

### 10.2 BillionTech LoS Integration Points

| API | Purpose | Direction |
|-----|---------|-----------|
| `/api/enrollment/kyc` | Trigger KYC verification | Call LoS |
| `/api/credit/bureau-pull` | Credit bureau check for higher-ticket requests | Call LoS |
| `/api/notifications/send` | Send notifications via LoS notification service | Call LoS |
| `/api/lms/handover` | Hand over disbursed loan for servicing in LMS | Call LoS |
| `/api/programs/status` | Expose program health data to LoS dashboard | Expose to LoS |
| `/api/programs/portfolio` | Expose portfolio data for consolidated reporting | Expose to LoS |

### 10.3 Integration Architecture Principles

- All integrations via REST APIs with OAuth 2.0 / API Key authentication
- Async processing for non-real-time integrations (salary data sync, invoice bulk upload)
- Circuit breaker pattern for external service calls
- Retry with exponential backoff for transient failures
- Webhook support for real-time event notifications from external systems
- Idempotent API design for payment operations

---

## 11. Regulatory & Compliance Requirements

### 11.1 RBI Digital Lending Directions 2025

| Req ID | Requirement | RBI Ref |
|--------|-------------|---------|
| REG-01 | All lending must be done in the name of the Regulated Entity (RE) — the licensed lender | Ch. III, Sec 8 |
| REG-02 | Loan must be disbursed directly to borrower's bank account (no pass-through via third party) | Ch. III, Sec 9 |
| REG-03 | Key Fact Statement (KFS) must be provided to borrower before loan execution | Ch. III, Sec 8 |
| REG-04 | Cooling-off/look-up period: borrower can exit without penalty (min 3 days for tenor > 7 days) | Ch. III, Sec 10 |
| REG-05 | Annual Percentage Rate (APR) must be disclosed in KFS | Ch. III, Sec 8 |
| REG-06 | Borrower data: only collect minimum necessary; store in India; explicit consent | Ch. IV, Sec 12-14 |
| REG-07 | Grievance Redressal: designated officer details in KFS, 30-day resolution SLA | Ch. III, Sec 11 |
| REG-08 | Report all loans to Credit Information Companies (CIBIL, Equifax, etc.) | Ch. V, Sec 16 |
| REG-09 | Digital Lending App (DLA) details to be reported to RBI | Ch. V, Sec 17 |
| REG-10 | Default Loss Guarantee (DLG) arrangements — if anchor provides guarantee, cap at 5% of portfolio | Ch. VI |
| REG-11 | Technology standards: data encryption, secure APIs, ISO 27001 compliance | Ch. IV, Sec 15 |

### 11.2 Other Regulatory Requirements

| Requirement | Regulation |
|-------------|-----------|
| GST on fees/interest spread (if applicable) | GST Act |
| TDS on interest (for eligible transactions) | Income Tax Act |
| Stamp duty on loan agreements (state-specific) | Indian Stamp Act |
| Fair Practices Code compliance | RBI FPC Guidelines |
| Anti-Money Laundering (AML/CFT) compliance | PMLA 2002 |
| Data Privacy — consent, purpose limitation, data minimization | Digital Personal Data Protection Act 2023 |
| MSME categorization for invoice discounting borrowers | MSMED Act |

---

## 12. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| **Performance** | Loan eligibility check response | < 2 seconds |
| **Performance** | Disbursement processing (post-approval) | < 15 minutes |
| **Performance** | Concurrent user support | 5,000+ simultaneous |
| **Performance** | Daily transaction processing capacity | 50,000+ |
| **Availability** | System uptime | 99.9% (excluding planned maintenance) |
| **Scalability** | Horizontal scaling of services | Auto-scale based on load |
| **Security** | Data at rest encryption | AES-256 |
| **Security** | Data in transit | TLS 1.3 |
| **Security** | API authentication | OAuth 2.0 + JWT |
| **Security** | Role-Based Access Control | Granular per module |
| **Security** | Sensitive data masking in logs | PII, financial data |
| **Audit** | All state changes audited | Full trail with user, timestamp, IP |
| **Compliance** | Data residency | India only |
| **Recovery** | RPO (Recovery Point Objective) | < 1 hour |
| **Recovery** | RTO (Recovery Time Objective) | < 4 hours |
| **Integration** | API response time to external consumers | < 500ms (p95) |
| **Monitoring** | Real-time dashboards | Program health, disbursement, repayment |

---

## 13. Business Rules & Parameters

### 13.1 Pay Day Loan — Business Rules

| Rule ID | Rule | Configurable |
|---------|------|--------------|
| PDL-BR-01 | Max loan amount = Earned Salary × Eligibility % (default 50%) | Yes — per program |
| PDL-BR-02 | Minimum employment tenure for eligibility: 6 months (configurable) | Yes — per program |
| PDL-BR-03 | Minimum net salary for eligibility: ₹15,000/month (configurable) | Yes — per program |
| PDL-BR-04 | Maximum concurrent active loans per borrower: 1 (configurable) | Yes — per program |
| PDL-BR-05 | Cooling-off between consecutive loans: 0 days (configurable) | Yes — per program |
| PDL-BR-06 | Auto-approve if: amount < ₹50K AND within 50% of limit AND no prior overdue | Yes — threshold configurable |
| PDL-BR-07 | If borrower has any DPD > 30 in last 6 months: reject | Yes |
| PDL-BR-08 | If employment terminated: freeze immediately, recall outstanding | Yes |
| PDL-BR-09 | Interest calculation: simple interest for tenure ≤ 30 days, reducing balance for > 30 days | Per product |
| PDL-BR-10 | Penal interest: 2% per month on overdue principal (configurable) | Yes |
| PDL-BR-11 | Earned salary calculation excludes: variable pay, one-time bonuses, reimbursements | Configurable components |
| PDL-BR-12 | Maximum tenure: until next salary date + 7 days buffer | Per program |

### 13.2 Invoice Discounting — Business Rules

| Rule ID | Rule | Configurable |
|---------|------|--------------|
| ID-BR-01 | Max discounting amount = Invoice Value × (100% - Margin%) where Margin default = 10% | Yes — per program |
| ID-BR-02 | Invoice age limit: max 30 days from invoice date (configurable) | Yes — per program |
| ID-BR-03 | Minimum invoice value: ₹50,000 (configurable) | Yes — per program |
| ID-BR-04 | Single invoice: must not exceed borrower's available limit | Must enforce |
| ID-BR-05 | Auto-approve if: invoice < ₹10L AND within 60% of limit AND anchor-confirmed AND no prior overdue | Yes |
| ID-BR-06 | Invoice must be GST-compliant (valid GSTIN, matching GST return filing) | Configurable |
| ID-BR-07 | No duplicate invoice financing: check by invoice number + seller GSTIN combination | Must enforce |
| ID-BR-08 | Max tenor: lesser of invoice due date or 180 days from discounting | Per program |
| ID-BR-09 | Discount rate calculation: per-day basis, actual/365 | Standard |
| ID-BR-10 | Penal interest: 2% per month on overdue (over and above normal discount rate) | Yes |
| ID-BR-11 | If anchor (seller) confirms invoice is disputed/cancelled: recall or adjust | Must enforce |
| ID-BR-12 | Concentration limit: max 30% of program limit to single borrower (configurable) | Yes — per program |
| ID-BR-13 | Three-way match (PO-GRN-Invoice): mandatory above ₹25L invoice value (configurable) | Yes |

### 13.3 Common Parameters (Configurable per Program)

| Parameter | Description | Default |
|-----------|-------------|---------|
| `program_limit` | Maximum total exposure under this program | Required |
| `anchor_limit` | Maximum exposure to this anchor | ≤ program_limit |
| `max_borrower_limit` | Maximum individual borrower limit | Required |
| `min_borrower_limit` | Minimum individual borrower limit | ₹5,000 |
| `interest_rate_min` | Minimum interest rate (% p.a.) | Per product |
| `interest_rate_max` | Maximum interest rate (% p.a.) | Per product |
| `default_interest_rate` | Default rate for new borrowers | Per product |
| `max_tenure_days` | Maximum loan tenure in days | Per product |
| `processing_fee_percent` | Processing fee as % of loan amount | 0-2% |
| `processing_fee_flat` | Flat processing fee (alternative) | ₹0 |
| `penal_rate_percent` | Penal interest rate on overdue (% per month) | 2% |
| `grace_period_days` | Days after due date before penal interest | 3 |
| `auto_approve_threshold` | Amount below which auto-approval applies | Per program |
| `max_concurrent_loans` | Maximum active loans per borrower | 1 (PDL), 10 (ID) |
| `eligibility_refresh_days` | Frequency of borrower eligibility re-evaluation | 30 |
| `cooling_off_days` | Mandatory cooling-off period post-disbursement | 3 (RBI min) |
| `concentration_limit_percent` | Max % of program to single borrower | 30% |

---

## 14. Reporting & MIS Requirements

### 14.1 Operational Reports

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Disbursement Summary | Daily | Treasury, Program Manager |
| Daily Collection/Repayment Summary | Daily | Treasury, Program Manager |
| Overdue/DPD Aging Report | Daily | Credit Analyst, Program Manager |
| Program Utilization Dashboard | Real-time | Program Manager, Management |
| Borrower Limit Utilization | Real-time | Program Manager |
| Pending Approvals Report | Real-time | Credit Analyst |
| Failed Disbursements Report | Daily | Operations |
| Failed Collections Report | Daily | Operations |

### 14.2 Risk & Analytics Reports

| Report | Frequency | Audience |
|--------|-----------|----------|
| Portfolio at Risk (PAR) — by program, anchor, product | Weekly | Risk, Management |
| NPA Report (90+ DPD) | Monthly | Risk, Compliance |
| Concentration Report (top borrowers, anchors) | Monthly | Risk |
| Vintage Analysis (by origination cohort) | Monthly | Risk, Management |
| Average Ticket Size & Tenure Analysis | Monthly | Product, Management |
| Borrower Retention & Repeat Usage | Monthly | Product |
| STP Rate Analysis | Weekly | Operations, Product |
| Turnaround Time (TAT) Analysis | Weekly | Operations |

### 14.3 Regulatory Reports

| Report | Frequency | Audience |
|--------|-----------|----------|
| Credit Bureau Reporting (CIBIL/Equifax file) | Monthly | Compliance |
| RBI DLA Reporting | Quarterly | Compliance |
| MSME Lending Report (for invoice discounting) | Quarterly | Compliance |
| Grievance Redressal Summary | Monthly | Compliance |
| DLG Utilization Report (if anchor provides guarantee) | Monthly | Risk, Compliance |

---

## 15. User Interface Requirements

### 15.1 Platform Admin / Program Manager UI (React Web App)

| Screen | Key Features |
|--------|-------------|
| **Dashboard** | Program utilization heat map, daily disbursement/collection, overdue summary, pending actions |
| **Programs** | List all programs, create/edit, view utilization, drill-down to anchors/borrowers |
| **Anchors** | Onboarding wizard, document upload, status management, linked programs |
| **Borrowers** | Search, filter, view profile, limit history, loan history, KYC status |
| **Loan Requests** | Queue for manual review, approve/reject with remarks, bulk actions |
| **Transactions** | Disbursement tracker, repayment tracker, reconciliation status |
| **Reports** | Generate/schedule reports, export (CSV/Excel/PDF) |
| **Configuration** | Product parameters, interest rates, fee structures, notification templates |
| **Users & Roles** | RBAC management, audit logs |

### 15.2 Anchor Portal (React Web App — separate login)

| Screen | Key Features |
|--------|-------------|
| **Dashboard** | Program utilization, active borrowers, overdue summary |
| **Borrowers** | View enrolled borrowers, their limits, utilization |
| **Data Upload** | Upload salary data (PDL) / invoice data (ID) — manual fallback |
| **Settlements** | View repayment schedule, upcoming deductions |
| **Reports** | Download utilization reports, borrower-wise summary |

### 15.3 Borrower Portal (React Web App — responsive/mobile-first)

| Screen | Key Features |
|--------|-------------|
| **Home** | Available limit, active loans, upcoming repayments |
| **Request Loan** | Select amount (slider), view charges, confirm, e-Sign |
| **Loan History** | Past and active loans, repayment status, download statements |
| **Profile** | KYC status, bank account, consent management |
| **Help/Grievance** | Raise grievance, track status, FAQs |

---

## 16. Acceptance Criteria

### 16.1 Pay Day Loan — Key Acceptance Criteria

1. **AC-PDL-01**: An employee registered under an active program can request a loan up to their eligible amount (earned salary × eligibility %) and receive funds within 15 minutes.
2. **AC-PDL-02**: The system correctly calculates earned salary based on days worked and excludes variable/non-guaranteed components.
3. **AC-PDL-03**: Loan is auto-approved if within configured thresholds; routed for manual review otherwise.
4. **AC-PDL-04**: KFS is generated and borrower consent obtained before disbursement.
5. **AC-PDL-05**: Repayment is automatically collected via salary deduction on salary date.
6. **AC-PDL-06**: Borrower's available limit is restored immediately after full repayment.
7. **AC-PDL-07**: System freezes borrower's account if employment termination is detected from HR system.
8. **AC-PDL-08**: Overdue loans correctly accrue penal interest and block new loan requests.

### 16.2 Invoice Discounting — Key Acceptance Criteria

1. **AC-ID-01**: A registered buyer can request discounting against verified invoices from their anchor (seller) and receive funds within 4 hours (STP) or same day (manual).
2. **AC-ID-02**: System correctly calculates discountable amount applying margin and checking against available limit.
3. **AC-ID-03**: Duplicate invoice detection prevents the same invoice from being financed twice.
4. **AC-ID-04**: Three-way match (PO-GRN-Invoice) is enforced for transactions above configured threshold.
5. **AC-ID-05**: Repayment is collected on invoice due date with correct interest calculation (actual days).
6. **AC-ID-06**: System handles partial payments applying FIFO to oldest outstanding.
7. **AC-ID-07**: Anchor and borrower both receive notifications for upcoming due dates and overdue events.
8. **AC-ID-08**: Concentration limits are enforced — system rejects discounting requests that would breach program concentration rules.

---

## 17. Assumptions & Constraints

### 17.1 Assumptions

| # | Assumption |
|---|-----------|
| A-1 | The lender operating this platform holds a valid NBFC/banking license from RBI |
| A-2 | Anchor (employer/seller) has an HR/ERP system capable of providing data via API or file upload |
| A-3 | Borrowers have Aadhaar-linked mobile numbers for OTP-based KYC |
| A-4 | Borrowers have bank accounts in their name (for direct disbursement compliance) |
| A-5 | BillionTech LoS APIs are available for KYC, credit bureau, and notification services |
| A-6 | Payment infrastructure (IMPS/NEFT/UPI/NACH) is accessible via payment gateway partner |
| A-7 | Anchors will execute Master Agreement before program activation |
| A-8 | For Pay Day Loan: employer agrees to salary deduction mechanism as primary repayment |
| A-9 | For Invoice Discounting: invoices are GST-compliant and filed in GST returns |
| A-10 | Platform will initially support English and Hindi (expandable to regional languages) |

### 17.2 Constraints

| # | Constraint |
|---|-----------|
| C-1 | Must comply with RBI Digital Lending Directions 2025 — no pass-through disbursement |
| C-2 | All data must be stored within India (data residency) |
| C-3 | Interest rates must comply with RBI fair practices code (no usurious rates) |
| C-4 | Platform must not operate as TReDS (requires separate PSO license from RBI) |
| C-5 | Pay Day Loan: Max loan tenure limited to 60 days (short-term product positioning) |
| C-6 | Invoice Discounting: Only purchase bill discounting (not factoring, which requires NBFC-Factor license) |
| C-7 | Tech stack: Java 21, Spring Boot 3.4, React, PostgreSQL 16, Redis, RabbitMQ (aligned with LoS) |
| C-8 | Must be deployable as standalone application (no hard dependency on LoS for core operations) |

---

## 18. Glossary

| Term | Definition |
|------|-----------|
| **Anchor** | The entity enabling a lending program — Employer (PDL) or Seller (ID) |
| **Borrower** | The entity taking the loan — Employee (PDL) or Buyer/Purchaser (ID) |
| **Lender** | The Regulated Entity (Bank/NBFC) providing funds |
| **Program** | A lending arrangement defined by a unique combination of Anchor + Lender + Product Type with specific parameters |
| **Earned Salary** | Salary accrued by an employee for days worked in the current pay cycle but not yet paid |
| **Invoice Discounting** | Financing against purchase invoices — buyer gets funds, repays on invoice due date |
| **Purchase Bill Discounting** | Same as Invoice Discounting from buyer's perspective — financing purchase bills |
| **KFS** | Key Fact Statement — RBI-mandated disclosure document showing APR, fees, charges |
| **NACH** | National Automated Clearing House — electronic payment system for auto-debit mandates |
| **DPD** | Days Past Due — number of days a repayment is overdue |
| **PAR** | Portfolio at Risk — outstanding of loans with any DPD |
| **NPA** | Non-Performing Asset — loan with DPD > 90 days |
| **STP** | Straight-Through Processing — fully automated without manual intervention |
| **DLG** | Default Loss Guarantee — guarantee provided by anchor/third party to cover defaults |
| **FOIR** | Fixed Obligation to Income Ratio |
| **APR** | Annual Percentage Rate — all-inclusive cost of loan annualized |
| **TReDS** | Trade Receivables Discounting System — RBI-regulated platform for MSME invoice discounting |
| **GRN** | Goods Receipt Note — document confirming receipt of goods by buyer |
| **EWA** | Earned Wage Access — alternate term for pay day loan/salary advance |
| **LTV** | Loan to Value ratio |
| **RE** | Regulated Entity — RBI-licensed Bank or NBFC |
| **LSP** | Lending Service Provider — entity providing services to RE in digital lending |
| **DLA** | Digital Lending App — customer-facing application used in digital lending |

---

## Document Approval

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Product Owner | | | |
| Business Head | | | |
| Compliance Officer | | | |
| Technology Head | | | |

---

*End of Business Requirements Document*
