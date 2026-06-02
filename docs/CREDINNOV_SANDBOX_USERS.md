# Credinnov sandbox — login users

**Branch:** `credinnov`  
**Password (all users):** `Bltest@123`  
**LOS URL:** `http://credinnov-sandbox.senseitech.com/los/`  
**PLP platform:** `http://credinnov-sandbox.senseitech.com/plp/`  
**PLP anchor:** `http://credinnov-sandbox.senseitech.com/plp-anchor/`  
**PLP borrower:** `http://credinnov-sandbox.senseitech.com/plp-borrower/`

Billionloans demo users are **deactivated** when migration `V57__seed_credinnov_los_auth_users.sql` (LOS) / `V3__seed_credinnov_users.sql` (PLP) runs.

## LOS (`los_users`)

| Display name | Email | Role |
|--------------|-------|------|
| Admin | admin@credinnov.com | Administrator |
| Credit Manager | creditmanager@credinnov.com | Credit Manager |
| Credit Officer | creditofficer@credinnov.com | Credit Officer |
| Credit Officer 2 | creditofficer2@credinnov.com | Credit Officer |
| Sales | sales@credinnov.com | Sales |
| Accounts | accounts@credinnov.com | Accounts |
| Borrower | borrower@credinnov.com | Borrower |

## PLP (`plp_iam.users`)

| Display name | Email | PLP role | Portal |
|--------------|-------|----------|--------|
| Admin | admin@credinnov.com | PLATFORM_ADMIN | `/plp/` |
| Credit Manager | creditmanager@credinnov.com | CREDIT_MANAGER | `/plp/` |
| Credit Officer | creditofficer@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Credit Officer 2 | creditofficer2@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Sales | sales@credinnov.com | COMPLIANCE_OFFICER | `/plp/` |
| Accounts | accounts@credinnov.com | ACCOUNTS_OFFICER | `/plp/` |
| Anchor Admin | anchor@credinnov.com | ANCHOR_ADMIN | `/plp-anchor/` |
| Borrower | borrower@credinnov.com | BORROWER | `/plp-borrower/` |

## PLP login / network error

Browsers must **not** call `http://localhost:8180` from the public site. Docker UIs mount `frontend/packages/*/docker/env-config.js`, which must use **`/plp-api`** when the hostname is `credinnov-sandbox.senseitech.com` (requires nginx `location /plp-api/`).

After updating env-config, **rebuild** PLP UI images (index.html must load `/plp/env-config.js`, not `/env-config.js`):

```bash
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build platform-ui anchor-portal borrower-portal
```

In the browser, open DevTools → Network → `env-config.js` should be  
`http://credinnov-sandbox.senseitech.com/plp/env-config.js` with `VITE_API_BASE_URL: '/plp-api'`.

Test API via nginx:

```bash
curl -s -X POST http://127.0.0.1/plp-api/api/v1/auth/login \
  -H 'Host: credinnov-sandbox.senseitech.com' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}'

### 502 / 503 on login

| Code | Meaning |
|------|---------|
| **502** (nginx HTML) | `plp-gateway` not listening on `127.0.0.1:8180` — container down or still starting |
| **503** (JSON from gateway) | Gateway up but **iam-service** not registered in Eureka yet (or IAM crashed) |

On EC2, run in order:

```bash
cd /vol/PLP-APP
git pull origin credinnov
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build
# Wait 2–3 minutes, then:

docker ps --filter name=plp --format "table {{.Names}}\t{{.Status}}"
curl -s http://127.0.0.1:8180/actuator/health
curl -s http://127.0.0.1:8181/actuator/health
curl -s -X POST http://127.0.0.1:8181/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}'
curl -s -X POST http://127.0.0.1:8180/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}'
```

If IAM direct (8181) works but gateway (8180) returns 503, wait for Eureka or check `docker logs plp-iam --tail 80` and `docker logs plp-gateway --tail 80`.

Eureka UI: http://127.0.0.1:8861 (from server) — **IAM-SERVICE** and **PROGRAM-SERVICE** should appear UP.

## LOS → PLP anchor sync (503)

Gateway route `POST /api/v1/integrations/los/anchors` → **program-service**. If login works but anchor sync returns 503, restart program service:

```bash
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build program-service
docker logs plp-program --tail 80
curl -s http://127.0.0.1:8182/actuator/health
```

LOS must use `admin@credinnov.com` / `Bltest@123` (`PLP_INTEGRATION_EMAIL` in LOS `.env.prod`).
```

## Deploy on EC2

```bash
# LOS
cd /vol/LOS_APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.prod.yml up -d --build los-core ui-service

# PLP
cd /vol/PLP-APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build iam-service
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d platform-ui anchor-portal borrower-portal
```

Flyway runs migrations on service startup. To re-run on existing DB, restart `los_core` and `plp-iam` (or run SQL manually if migrations already applied).
