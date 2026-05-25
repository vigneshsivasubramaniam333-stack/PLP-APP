# Quick check that PLP Postgres is reachable on host port 5433
$ErrorActionPreference = "Continue"

Write-Host "PLP Postgres check (localhost:5433)" -ForegroundColor Cyan

$listening = Get-NetTCPConnection -LocalPort 5433 -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Host "[FAIL] Nothing listening on port 5433." -ForegroundColor Red
    Write-Host "Run: docker compose -f docker-compose.infra.yml up -d" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Port 5433 is listening." -ForegroundColor Green

$container = docker ps --filter "name=plp-postgres" --format "{{.Names}} {{.Status}}" 2>$null
if ($container) {
    Write-Host "[OK] Container: $container" -ForegroundColor Green
    $ready = docker exec plp-postgres pg_isready -U plp_admin -d plp_db 2>&1
    Write-Host "pg_isready: $ready"
} else {
    Write-Host "[WARN] plp-postgres container not found (port may be used by another process)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "JDBC URL used by PLP services (local profile):" -ForegroundColor Cyan
Write-Host "  jdbc:postgresql://localhost:5433/plp_db?currentSchema=<service_schema>" -ForegroundColor White
