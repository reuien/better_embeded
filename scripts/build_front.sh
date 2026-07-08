#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cd front
npm install
npm run build
