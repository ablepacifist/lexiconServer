#!/bin/bash
# Lexicon Server Startup Script
# Loads .env file and starts the server with environment variables

cd "$(dirname "$0")"

# Load layered environment variables, same order as spring.config.import in
# application.properties: this module's own .env first (if present), then
# the monorepo root .env (if present) - sourced second so it overrides the
# module's own values for any key both files set. Neither file is required.
set -a
if [ -f .env ]; then
    echo "Loading environment variables from .env..."
    source .env
fi
MASTER_ENV_FILE="${MASTER_ENV_FILE:-../.env}"
if [ -f "$MASTER_ENV_FILE" ]; then
    echo "Loading environment variables from $MASTER_ENV_FILE..."
    source "$MASTER_ENV_FILE"
fi
set +a
if [ -f .env ] || [ -f "$MASTER_ENV_FILE" ]; then
    echo "✓ Environment variables loaded"
    echo "  YTDLP_COOKIES_PATH: $YTDLP_COOKIES_PATH"
else
    echo "Warning: no .env file found (module or root) - running on built-in defaults"
fi

# Start the server
echo ""
echo "Starting Lexicon Server..."
./gradlew bootRun
