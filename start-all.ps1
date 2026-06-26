# Start PLP backend alongside LOS (LOS unchanged).
# Uses environment variables (works without rebuilding JARs).
# Prerequisites: docker compose -f docker-compose.infra.yml up -d

$ErrorActionPreference = "Stop"
$Root = "D:\PLP\PLP-APP"
$base = "$Root\services"
$debugLog = "D:\LOS\los-app\los-app\debug-8950a2.log"
$eurekaUrl = "http://localhost:8861/eureka/"

function Write-DebugLog([string]$hypothesisId, [string]$message, [hashtable]$data) {
    #region agent log
    $entry = @{
        sessionId    = "8950a2"
        hypothesisId = $hypothesisId
        location     = "start-all.ps1"
        message      = $message
        data         = $data
        timestamp    = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    } | ConvertTo-Json -Compress
    Add-Content -Path $debugLog -Value $entry -Encoding utf8
    #endregion
}

function Test-PortListening([int]$Port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Build-StartCommand([string]$JarPath, [hashtable]$Env) {
    $lines = @()
    foreach ($k in $Env.Keys) {
        $v = $Env[$k] -replace "'", "''"
        $lines += "`$env:$k='$v'"
    }
    $lines += "java -jar '$JarPath'"
    return ($lines -join "; ")
}

# Host-run JARs must use localhost (defaults in application.yml point at Docker service names).
$localSpringProfile = @{
    SPRING_PROFILES_ACTIVE = "local"
}
$localDevReset = @{
    PLP_DEV_RESET_ENABLED = "true"
}
$localRedis = @{
    SPRING_DATA_REDIS_HOST = "localhost"
    SPRING_DATA_REDIS_PORT = "6380"
}
$localRabbit = @{
    SPRING_RABBITMQ_HOST = "localhost"
    SPRING_RABBITMQ_PORT = "5673"
}

# Per-service env overrides (Postgres host port 5433, infra remapped in docker-compose.infra.yml)
$services = @(
    @{
        name = "Discovery Service"
        jar  = "discovery-service\target\discovery-service-1.0.0-SNAPSHOT.jar"
        port = 8861
        env  = @{
            SERVER_PORT = "8861"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile
    },
    @{
        name = "API Gateway"
        jar  = "api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar"
        port = 8180
        env  = @{
            SERVER_PORT = "8180"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile + $localRedis
    },
    @{
        name = "IAM Service"
        jar  = "iam-service\target\iam-service-1.0.0-SNAPSHOT.jar"
        port = 8181
        env  = @{
            SERVER_PORT = "8181"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_iam"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile + $localRedis + $localDevReset
    },
    @{
        name = "Program Service"
        jar  = "program-service\target\program-service-1.0.0-SNAPSHOT.jar"
        port = 8182
        env  = @{
            SERVER_PORT = "8182"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_program"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
            PLP_STORAGE_MINIO_ENDPOINT = "http://localhost:9010"
            PLP_IAM_BASE_URL = "http://localhost:8181"
            PLP_LENDING_BASE_URL = "http://localhost:8183"
            PLP_LOS_INTEGRATION_API_KEY = "plp-los-integration-dev-key"
        } + $localSpringProfile + $localRedis + $localRabbit + $localDevReset
    },
    @{
        name = "Lending Service"
        jar  = "lending-service\target\lending-service-1.0.0-SNAPSHOT.jar"
        port = 8183
        env  = @{
            SERVER_PORT = "8183"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_lending"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
            PLP_PUBLIC_API_BASE_URL = "http://localhost:8180"
            PLP_BORROWER_UI_URL = "http://localhost:3012/plp-borrower"
            LOS_BORROWER_UI_URL = "http://localhost:5173/los/borrower"
        } + $localSpringProfile + $localRedis + $localRabbit + $localDevReset
    },
    @{
        name = "Integration Service"
        jar  = "integration-service\target\integration-service-1.0.0-SNAPSHOT.jar"
        port = 8184
        env  = @{
            SERVER_PORT = "8184"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_integration"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile + $localRabbit
    },
    @{
        name = "Notification Service"
        jar  = "notification-service\target\notification-service-1.0.0-SNAPSHOT.jar"
        port = 8185
        env  = @{
            SERVER_PORT = "8185"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_notification"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile + $localRabbit
    },
    @{
        name = "Report Service"
        jar  = "report-service\target\report-service-1.0.0-SNAPSHOT.jar"
        port = 8186
        env  = @{
            SERVER_PORT = "8186"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/plp_db?currentSchema=plp_report&stringtype=unspecified"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        } + $localSpringProfile + $localRabbit
    }
)

Write-DebugLog "H1" "start-all invoked" @{ eurekaUrl = $eurekaUrl }

Write-Host "PLP start-all (env-based config for Postgres :5433)" -ForegroundColor Cyan

function Wait-ForPostgres {
    param([int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortListening 5433)) {
            Start-Sleep -Seconds 2
            continue
        }
        docker exec plp-postgres pg_isready -U plp_admin -d plp_db 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

if (-not (Wait-ForPostgres)) {
    Write-Host "ERROR: PLP Postgres is not ready on 127.0.0.1:5433. Start infra first:" -ForegroundColor Red
    Write-Host "  docker compose -f docker-compose.infra.yml up -d" -ForegroundColor Yellow
    Write-DebugLog "H3" "postgres 5433 not ready" @{}
    exit 1
}

Write-Host "[OK] Postgres ready on 127.0.0.1:5433" -ForegroundColor Green

function Start-PlpService($svc) {
    $jarPath = Join-Path $base $svc.jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "ERROR: Missing $jarPath - run: .\mvnw.cmd install -DskipTests" -ForegroundColor Red
        exit 1
    }
    $cmd = Build-StartCommand $jarPath $svc.env
    Write-Host "Starting $($svc.name) -> port $($svc.port)..." -ForegroundColor Cyan
    Write-DebugLog "H1" "start service" @{ name = $svc.name; port = $svc.port }
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd
}

Start-PlpService $services[0]

Write-Host "Waiting 25s for Discovery on :8861..." -ForegroundColor Yellow
Start-Sleep -Seconds 25

if (Test-PortListening 8861) {
    Write-DebugLog "H1" "discovery port up" @{ port = 8861 }
    Write-Host "[OK] Discovery listening on 8861" -ForegroundColor Green
} else {
    Write-DebugLog "H1" "discovery port still down" @{ port = 8861 }
    Write-Host "[WARN] Discovery not on 8861 yet - check its window" -ForegroundColor Yellow
}

foreach ($s in $services[1..($services.Length - 1)]) {
    Start-PlpService $s
    Start-Sleep -Seconds 4
}

Write-Host ""
Write-Host "Done. Run .\check-health.ps1 after ~60s" -ForegroundColor Green
Write-Host "  Eureka: http://localhost:8861  Gateway: http://localhost:8180" -ForegroundColor Green
Write-DebugLog "H1" "start-all finished" @{}
