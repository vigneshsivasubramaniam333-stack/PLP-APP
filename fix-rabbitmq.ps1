# Fix plp-rabbitmq ".erlang.cookie: eacces" on Windows Docker.
Set-Location $PSScriptRoot

Write-Host "Recreating PLP RabbitMQ (no data volume - Windows-safe)..." -ForegroundColor Cyan
docker compose stop rabbitmq | Out-Null
docker rm -f plp-rabbitmq | Out-Null

foreach ($v in @("plp_plp_rabbitmq_data", "plp_rabbitmq_data")) {
    docker volume rm $v 2>$null | Out-Null
}

docker compose up -d rabbitmq
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Waiting for RabbitMQ (up to 90s)..." -ForegroundColor Yellow
$ok = $false
for ($i = 0; $i -lt 18; $i++) {
    Start-Sleep -Seconds 5
    $state = docker inspect plp-rabbitmq --format '{{.State.Status}}' 2>$null
    if ($state -eq 'exited') {
        Write-Host "RabbitMQ exited. Last log lines:" -ForegroundColor Red
        docker logs plp-rabbitmq --tail 15
        exit 1
    }
    $h = docker inspect plp-rabbitmq --format '{{.State.Health.Status}}' 2>$null
    if ($h -eq 'healthy') { $ok = $true; break }
}

if ($ok) {
    Write-Host "[OK] RabbitMQ is healthy." -ForegroundColor Green
}
else {
    Write-Host "Status: $(docker inspect plp-rabbitmq --format '{{.State.Status}} / health={{.State.Health.Status}}')" -ForegroundColor Yellow
}

Write-Host "Starting remaining PLP services..." -ForegroundColor Cyan
docker compose up -d

Write-Host "Done. Check: docker compose ps" -ForegroundColor Green
