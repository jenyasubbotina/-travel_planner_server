#!/usr/bin/env sh
set -e
F="${COMPOSE_FILE:-docker-compose.prod.yml}"

run() {
  if docker compose version >/dev/null 2>&1; then
    sudo docker compose -f "$F" "$@"
  else
    sudo docker-compose -f "$F" "$@"
  fi
}

run pull app
if docker compose version >/dev/null 2>&1; then
  run up -d
else
  run stop app 2>/dev/null || true
  run rm -f app 2>/dev/null || true
  run up -d
fi
