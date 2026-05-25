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

# Per-service env overrides (Postgres host port 5433, infra remapped in docker-compose.infra.yml)
$services = @(
    @{
        name = "Discovery Service"
        jar  = "discovery-service\target\discovery-service-1.0.0-SNAPSHOT.jar"
        port = 8861
        env  = @{
            SERVER_PORT = "8861"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "API Gateway"
        jar  = "api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar"
        port = 8180
        env  = @{
            SERVER_PORT = "8180"
            SPRING_DATA_REDIS_PORT = "6380"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "IAM Service"
        jar  = "iam-service\target\iam-service-1.0.0-SNAPSHOT.jar"
        port = 8181
        env  = @{
            SERVER_PORT = "8181"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_iam"
            SPRING_DATA_REDIS_PORT = "6380"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "Program Service"
        jar  = "program-service\target\program-service-1.0.0-SNAPSHOT.jar"
        port = 8182
        env  = @{
            SERVER_PORT = "8182"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_program"
            SPRING_DATA_REDIS_PORT = "6380"
            SPRING_RABBITMQ_PORT = "5673"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
            PLP_STORAGE_MINIO_ENDPOINT = "http://localhost:9010"
        }
    },
    @{
        name = "Lending Service"
        jar  = "lending-service\target\lending-service-1.0.0-SNAPSHOT.jar"
        port = 8183
        env  = @{
            SERVER_PORT = "8183"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_lending"
            SPRING_DATA_REDIS_PORT = "6380"
            SPRING_RABBITMQ_PORT = "5673"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "Integration Service"
        jar  = "integration-service\target\integration-service-1.0.0-SNAPSHOT.jar"
        port = 8184
        env  = @{
            SERVER_PORT = "8184"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_integration"
            SPRING_RABBITMQ_PORT = "5673"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "Notification Service"
        jar  = "notification-service\target\notification-service-1.0.0-SNAPSHOT.jar"
        port = 8185
        env  = @{
            SERVER_PORT = "8185"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_notification"
            SPRING_RABBITMQ_PORT = "5673"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    },
    @{
        name = "Report Service"
        jar  = "report-service\target\report-service-1.0.0-SNAPSHOT.jar"
        port = 8186
        env  = @{
            SERVER_PORT = "8186"
            SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/plp_db?currentSchema=plp_report&stringtype=unspecified"
            SPRING_RABBITMQ_PORT = "5673"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaUrl
        }
    }
)

Write-DebugLog "H1" "start-all invoked" @{ eurekaUrl = $eurekaUrl }

Write-Host "PLP start-all (env-based config for Postgres :5433)" -ForegroundColor Cyan

if (-not (Test-PortListening 5433)) {
    Write-Host "WARNING: Port 5433 not listening. Start infra first:" -ForegroundColor Yellow
    Write-Host "  docker compose -f docker-compose.infra.yml up -d" -ForegroundColor Yellow
    Write-DebugLog "H3" "postgres 5433 down" @{}
}

function Start-PlpService($svc) {
    $jarPath = Join-Path $base $svc.jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "ERROR: Missing $jarPath — run: .\mvnw.cmd install -DskipTests" -ForegroundColor Red
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
    Write-Host "[WARN] Discovery not on 8861 yet — check its window" -ForegroundColor Yellow
}

foreach ($s in $services[1..($services.Length - 1)]) {
    Start-PlpService $s
    Start-Sleep -Seconds 4
}

Write-Host ""
Write-Host "Done. Run .\check-health.ps1 after ~60s" -ForegroundColor Green
Write-Host "  Eureka: http://localhost:8861  Gateway: http://localhost:8180" -ForegroundColor Green
Write-DebugLog "H1" "start-all finished" @{}
