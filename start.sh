#!/bin/bash
# Load layered environment variables, same order as spring.config.import in
# application.properties: this module's own .env first (if present), then
# the monorepo root .env (if present) - sourced second so it overrides the
# module's own values for any key both files set. Neither file is required.
set -a
[ -f .env ] && source .env
MASTER_ENV_FILE="${MASTER_ENV_FILE:-../.env}"
[ -f "$MASTER_ENV_FILE" ] && source "$MASTER_ENV_FILE"
set +a

# Start the server
./gradlew bootRun
