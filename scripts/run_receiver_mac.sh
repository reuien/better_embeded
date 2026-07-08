#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cd receiver
mvn spring-boot:run
