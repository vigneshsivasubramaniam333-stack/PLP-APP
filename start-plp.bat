@echo off
title Program Lending Platform Starter

cd /d D:\CurrentAug032025\Platform\ProgramLending

echo Starting Program Lending Platform...

start "PLP - API Gateway" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/api-gateway spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - IAM Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/iam-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Program Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/program-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Lending Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/lending-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Integration Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/integration-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Notification Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/notification-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Report Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/report-service spring-boot:run -Dspring-boot.run.profiles=local"

start "PLP - Discovery Service" cmd /k "cd /d D:\CurrentAug032025\Platform\ProgramLending && mvn -pl services/discovery-service spring-boot:run -Dspring-boot.run.profiles=local"

timeout /t 10

echo Starting frontends...

start "PLP - Platform UI" cmd /k "cd D:\CurrentAug032025\Platform\ProgramLending\frontend && npm run dev --workspace=@plp/platform-ui"

start "PLP - Anchor Portal" cmd /k "cd D:\CurrentAug032025\Platform\ProgramLending\frontend && npm run dev --workspace=@plp/anchor-portal"

start "PLP - Borrower Portal" cmd /k "cd D:\CurrentAug032025\Platform\ProgramLending\frontend && npm run dev --workspace=@plp/borrower-portal"

echo.
echo All PLP services started in separate windows.
pause