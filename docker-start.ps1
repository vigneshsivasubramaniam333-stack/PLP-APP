# Start PLP stack in Docker Desktop (alongside LOS on remapped host ports).
param(
    [switch]$InfraOnly,
    [switch]$WithUi,
    [switch]$Build
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker is not installed or not on PATH. Open Docker Desktop first." -ForegroundColor Red
    exit 1
}

if ($InfraOnly) {
    Write-Host "Starting PLP infrastructure only..." -ForegroundColor Cyan
    docker compose -f docker-compose.infra.yml up -d
    exit $LASTEXITCODE
}

$composeFiles = @("-f", "docker-compose.yml")
if ($WithUi) {
    $composeFiles += @("-f", "docker-compose.ui.yml")
}

$services = @(
    "postgres", "redis", "rabbitmq", "minio",
    "discovery-service",
    "iam-service", "program-service", "lending-service",
    "integration-service", "notification-service", "report-service",
    "api-gateway"
)
if ($WithUi) {
    $services += "platform-ui"
}

$uiMode = if ($WithUi) { "Docker build" } else { "local npm (see below)" }
Write-Host "Starting PLP backend in Docker (UI: $uiMode)..." -ForegroundColor Cyan

if ($Build) {
    Write-Host "Building images (first backend build ~10-20 min)..." -ForegroundColor Yellow
    docker compose @composeFiles build @services
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

docker compose @composeFiles up -d @services
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "PLP Docker backend is up." -ForegroundColor Green
Write-Host "  API Gateway:  http://localhost:8180/actuator/health" -ForegroundColor Green
Write-Host "  Eureka:       http://localhost:8861" -ForegroundColor Green
Write-Host "  Postgres:     localhost:5433 (plp_db / plp_admin)" -ForegroundColor Green
Write-Host "  RabbitMQ UI:  http://localhost:15673" -ForegroundColor Green

if ($WithUi) {
    Write-Host "  Platform UI:  http://localhost:3100/plp/" -ForegroundColor Green
}
else {
    Write-Host ""
    Write-Host "Frontend (recommended - use local npm, avoids Docker registry timeout):" -ForegroundColor Cyan
    Write-Host "  cd frontend" -ForegroundColor White
    Write-Host "  npm install" -ForegroundColor White
    Write-Host "  npm run dev:platform" -ForegroundColor White
    Write-Host "  Open http://localhost:3000/plp/" -ForegroundColor White
    Write-Host ""
    Write-Host "To build UI in Docker later: .\docker-start.ps1 -Build -WithUi" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Logs:  docker compose logs -f api-gateway" -ForegroundColor Cyan
Write-Host "Stop:  docker compose down" -ForegroundColor Cyan
