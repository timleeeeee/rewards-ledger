#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rewards-ledger}"
cd "$APP_DIR"

if [ ! -f docker-compose.yml ]; then
  echo "docker-compose.yml not found in $APP_DIR"
  exit 1
fi

if [ ! -f .env ]; then
  echo ".env not found in $APP_DIR"
  exit 1
fi

docker compose pull || true
docker compose up -d --build --remove-orphans
docker image prune -f --filter "until=24h"
docker compose ps
