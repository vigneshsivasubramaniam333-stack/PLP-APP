#!/bin/sh
set -e
mkdir -p /data/digital-invoices
chown -R plp:plp /data/digital-invoices 2>/dev/null || true
exec su-exec plp java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar "$@"
