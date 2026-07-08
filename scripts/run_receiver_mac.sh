#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
MAC_IP="$(ipconfig getifaddr en0 || true)"
echo "macOS receiver expected IP: ${MAC_IP:-unknown}"
echo "Receiver dashboard: http://${MAC_IP:-127.0.0.1}:8080/"
echo "Receiver health:    http://${MAC_IP:-127.0.0.1}:8080/api/system-status"
echo
cd receiver
mvn spring-boot:run
