# Program Lending Platform (PLP)

Standalone lending platform supporting **Pay Day Loan** (Earned Wage Access) and **Invoice Discounting** (Purchase Bill Discounting) products with a Program-based model.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (:8080)                           │
│              JWT validation, routing, rate limiting              │
└────────────┬───────────┬──────────────┬────────────┬────────────┘
             │           │              │            │
    ┌────────▼──┐  ┌─────▼────┐  ┌─────▼────┐  ┌───▼──────────┐
    │IAM Service│  │ Program  │  │ Lending  │  │ Integration  │
    │  (:8081)  │  │ Service  │  │ Service  │  │   Service    │
    │           │  │ (:8082)  │  │ (:8083)  │  │   (:8084)    │
    └───────────┘  └──────────┘  └──────────┘  └──────────────┘
             │           │              │            │
    ┌────────▼───────────▼──────────────▼────────────▼────────────┐
    │              PostgreSQL 16 (schema-per-service)              │
    └─────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, Spring Cloud 2024 |
| Frontend | React 18, Vite 6, TypeScript, Tailwind CSS 4 |
| Database | PostgreSQL 16 (schema-per-service) |
| Cache | Redis 7.2 (limit management, sessions) |
| Messaging | RabbitMQ 3.13 (notifications, events) |
| Storage | MinIO (documents) |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |

## Services

| Service | Port | Responsibility |
|---------|------|---------------|
| discovery-service | 8761 | Eureka service registry |
| api-gateway | 8080 | JWT validation, routing, CORS |
| iam-service | 8081 | Authentication, authorization, RBAC |
| program-service | 8082 | Programs, Anchors, Borrowers, Limits |
| lending-service | 8083 | Loans, Disbursements, Repayments |
| integration-service | 8084 | HR/ERP/Payment adapters |
| notification-service | 8085 | Email/SMS via RabbitMQ |
| report-service | 8086 | Reporting, MIS, Audit |

## Frontend Portals

| Portal | Port | Users |
|--------|------|-------|
| Platform UI | 3000 | Platform Admin, Program Managers |
| Anchor Portal | 3001 | Employers, Sellers |
| Borrower Portal | 3002 | Employees, Buyers |

## Quick Start

### Prerequisites
- Java 21 (JDK)
- Node.js 20+
- Docker & Docker Compose

### Infrastructure
```bash
docker compose -f docker-compose.infra.yml up -d
```

### Backend
```bash
./mvnw clean install -DskipTests
# Start services in order:
./mvnw spring-boot:run -pl services/discovery-service
./mvnw spring-boot:run -pl services/api-gateway
./mvnw spring-boot:run -pl services/iam-service
./mvnw spring-boot:run -pl services/program-service
./mvnw spring-boot:run -pl services/lending-service
./mvnw spring-boot:run -pl services/integration-service
```

### Frontend
```bash
cd frontend
npm install
npm run dev:platform    # http://localhost:3000
```

### Default Credentials
- **Platform Admin**: admin@plp.com / Admin@PLP2026

## Products

### Pay Day Loan (Earned Wage Access)
- **Anchor**: Employer
- **Borrower**: Employee
- **Collateral**: Earned/accumulated salary
- **Tenure**: 7–60 days
- **LTV**: 50–80% of earned salary

### Invoice Discounting (Purchase Bill Discounting)
- **Anchor**: Seller (invoice raiser)
- **Borrower**: Purchaser/Buyer
- **Collateral**: Purchase invoice
- **Tenure**: 30–180 days
- **LTV**: 70–90% of invoice value

## Program Concept

```
Lender (Bank/NBFC)
  └── Program (Anchor + Lender + Product)
        ├── Program-level limits & parameters
        ├── Anchor-level configuration
        └── Borrower-level limits (per borrower)
```

## Documentation
- [Business Requirements Document](docs/BRD_Program_Lending_Platform.md)
- [High-Level Design Document](docs/HLD_Program_Lending_Platform.md)
