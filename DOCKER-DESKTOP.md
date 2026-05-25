# Run PLP with Docker Desktop (alongside LOS)

LOS stays on its default ports. PLP uses **remapped host ports** so both can run together.

## Prerequisites

1. **Docker Desktop** installed and running (whale icon steady in the tray).
2. Enough RAM — recommend **8 GB+** for Docker (Settings → Resources).
3. LOS can keep running; no LOS changes required.

## Quick start (recommended: backend in Docker, UI via npm)

Avoids `npm install` inside Docker (common `EIDLETIMEOUT` on slow networks).

```powershell
cd D:\PLP\PLP-APP

# First time — backend only (~10–20 min)
.\docker-start.ps1 -Build

# Frontend (separate terminal)
cd frontend
npm install
npm run dev:platform
# Open http://localhost:3000/plp/
```

Later backend restarts:

```powershell
.\docker-start.ps1
```

**Do not** run bare `docker compose up -d --build` unless you use the service list below — it used to build `platform-ui` and fail on npm timeouts.

Backend only via compose:

```powershell
docker compose up -d --build postgres redis rabbitmq minio discovery-service iam-service program-service lending-service integration-service notification-service report-service api-gateway
```

## Optional: UI in Docker

```powershell
.\docker-start.ps1 -Build -WithUi
# Open http://localhost:3100/plp/
```

If npm still times out in Docker, use the npm-on-host flow above.

## URLs

| Service | URL |
|---------|-----|
| **Platform UI** | http://localhost:3100/plp/ |
| API Gateway | http://localhost:8180 |
| Eureka | http://localhost:8861 |
| RabbitMQ management | http://localhost:15673 (`plp_rabbit` / `plp_rabbit_2026`) |
| MinIO console | http://localhost:9011 (`plp_minio_admin` / `plp_minio_secret_2026`) |

## Postgres (host port 5433)

| Field | Value |
|-------|--------|
| Host | `localhost` |
| Port | **5433** |
| Database | `plp_db` |
| User | `plp_admin` |
| Password | `plp_secret_2026` |

Inside Docker, services use hostname `postgres:5432`. From your PC (DBeaver, psql), use **5433**.

```powershell
docker exec -it plp-postgres psql -U plp_admin -d plp_db
```

## Useful commands

```powershell
# Status
docker compose ps

# Logs (one service)
docker compose logs -f api-gateway

# Stop PLP (keeps data volumes)
docker compose down

# Stop and wipe PLP DB/data
docker compose down -v
```

## Options

```powershell
# Infrastructure only — run JARs locally with start-all.ps1
.\docker-start.ps1 -InfraOnly

# UI container (optional)
.\docker-start.ps1 -Build -WithUi
```

## Port map vs LOS

| PLP (Docker host) | LOS (typical) |
|-------------------|---------------|
| 5433 | 5432 |
| 6380 | 6379 |
| 5673 / 15673 | 5672 / 15672 |
| 9010 / 9011 | 9000 / 9001 |
| 8861 | 8761 |
| 8180–8186 | 8080–8086 |
| 3100 (UI) | 5173 or 80 |

## Troubleshooting

**Port already in use**  
Another app (often LOS) owns the port. Stop the conflicting container or change the left side of the port mapping in `docker-compose.yml`.

**Service keeps restarting**  
`docker compose logs <service-name>` — often Flyway/DB not ready; wait for `plp-postgres` healthy, then `docker compose up -d` again.

**UI loads but API fails**  
Confirm gateway: http://localhost:8180/actuator/health  
`env-config.js` should point to `http://localhost:8180` (mounted from `frontend/packages/platform-ui/docker/env-config.js`).

**Build fails on Windows**  
Ensure Docker Desktop uses Linux containers and has WSL2 backend enabled.

**`plp-rabbitmq exited (1)` / `.erlang.cookie: eacces`**  
Windows Docker + RabbitMQ’s default `/var/lib/rabbitmq` volume. `docker-compose.yml` uses `tmpfs` for that path. Recreate RabbitMQ:

```powershell
.\fix-rabbitmq.ps1
```

**`npm error EIDLETIMEOUT` on platform-ui**  
npm could not reach `registry.npmjs.org` from inside Docker (~9 min idle). Use backend-only Docker + local `npm run dev:platform`, or retry `.\docker-start.ps1 -Build -WithUi` on a faster network.
