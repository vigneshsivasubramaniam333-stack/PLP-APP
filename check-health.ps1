# Health check for PLP local profile (alongside LOS)
$services = @(
    @{ name = "Discovery Service";    port = 8861; url = "http://localhost:8861/actuator/health" },
    @{ name = "API Gateway";          port = 8180; url = "http://localhost:8180/actuator/health" },
    @{ name = "IAM Service";          port = 8181; url = "http://localhost:8181/actuator/health" },
    @{ name = "Program Service";      port = 8182; url = "http://localhost:8182/actuator/health" },
    @{ name = "Lending Service";      port = 8183; url = "http://localhost:8183/actuator/health" },
    @{ name = "Integration Service";  port = 8184; url = "http://localhost:8184/actuator/health" },
    @{ name = "Notification Service"; port = 8185; url = "http://localhost:8185/actuator/health" },
    @{ name = "Report Service";       port = 8186; url = "http://localhost:8186/actuator/health" }
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   PLP SERVICE HEALTH (local profile)   " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$allUp = $true

foreach ($s in $services) {
    try {
        $response = Invoke-RestMethod -Uri $s.url -TimeoutSec 5
        if ($response.status -eq "UP") {
            Write-Host "  [UP]   $($s.name) (port $($s.port))" -ForegroundColor Green
        } else {
            Write-Host "  [??]   $($s.name) (port $($s.port)) — status: $($response.status)" -ForegroundColor Yellow
            $allUp = $false
        }
    } catch {
        Write-Host "  [DOWN] $($s.name) (port $($s.port)) — not reachable" -ForegroundColor Red
        $allUp = $false
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($allUp) {
    Write-Host "  All services are UP and healthy!" -ForegroundColor Green
} else {
    Write-Host "  Some services are DOWN. Check their PowerShell windows for errors." -ForegroundColor Red
    Write-Host "  Common fix: ensure Postgres is on :5433 (docker compose -f docker-compose.infra.yml up -d)" -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Eureka Dashboard: http://localhost:8861" -ForegroundColor Cyan
Write-Host "Platform UI:      http://localhost:3000/plp/" -ForegroundColor Cyan
