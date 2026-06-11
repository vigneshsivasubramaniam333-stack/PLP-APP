# Run PLP alongside LOS (LOS unchanged)

LOS keeps its default ports. PLP uses remapped infrastructure + `local` Spring profile.

## Port map

| Component | LOS (typical) | PLP (local) |
|-----------|---------------|-------------|
| PostgreSQL | 5432 | **5433** |
| Redis | 6379 | **6380** |
| RabbitMQ | 5672 / 15672 | **5673** / **15673** |
| MinIO | 9000 / 9001 | **9010** / **9011** |
| Eureka | 8761 | **8861** |
| API Gateway | 8080 | **8180** |
| IAM … Report | 8081–8086 | **8181–8186** |
| Platform UI | 5173 or 3000 | **3000** |

## 1. PLP Postgres on port 5433

`docker-compose.infra.yml` already maps host **5433** → container 5432.

```powershell
cd D:\PLP\PLP-APP
docker compose -f docker-compose.infra.yml up -d
```

Verify:

```powershell
docker ps --filter name=plp-postgres
docker exec plp-postgres pg_isready -U plp_admin -d plp_db
```

Connect (optional — DBeaver, psql):

- Host: `localhost`
- Port: `5433`
- Database: `plp_db`
- User: `plp_admin`
- Password: `plp_secret_2026`

Schemas are created on first container start from `init-databases.sql` (`plp_iam`, `plp_program`, `plp_lending`, etc.). Flyway in each service applies migrations.

**Fresh database:** `docker compose -f docker-compose.infra.yml down -v` then `up -d` (destroys PLP data only).

## 2. Rebuild JARs (after `application-local.yml` was added)

```powershell
cd D:\PLP\PLP-APP
.\mvnw.cmd install -DskipTests
```

## 3. Start all backend services

```powershell
.\start-all.ps1
```

This uses `-Dspring.profiles.active=local` so each service reads `application-local.yml` (Postgres **5433**, Eureka **8861**, etc.).

## 4. Health check

```powershell
.\check-health.ps1
```

## 5. Frontend

```powershell
cd frontend
npm install
npm run dev:platform
```

Open http://localhost:3000/plp/

## Docker Desktop (full stack)

See **[DOCKER-DESKTOP.md](./DOCKER-DESKTOP.md)** for `docker compose up` / `.\docker-start.ps1 -Build`.

## Optional: LOS → PLP sync

On LOS core (env vars when starting `los-core-service` with `local` profile):

```powershell
$env:PLP_BASE_URL = "http://localhost:8180"
$env:PLP_SYNC_ENABLED = "true"
$env:PLP_INTEGRATION_EMAIL = "admin@credinnov.com"
$env:PLP_INTEGRATION_PASSWORD = "Bltest@123"
```

Program-service (host JARs) must reach IAM on **8181** — `start-all.ps1` sets `PLP_IAM_BASE_URL=http://localhost:8181`. Without this, anchor sync hangs calling `iam-service:8081`.

After Credinnov seed migration (`V3__seed_credinnov_users.sql`), `admin@plp.com` is **INACTIVE** in PLP IAM — LOS anchor/borrower push will fail with **401** if integration login still uses that account.

Verify PLP login from the same machine as LOS:

```powershell
Invoke-RestMethod -Uri "http://localhost:8180/api/v1/auth/login" -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"admin@credinnov.com","password":"Bltest@123"}'
```
