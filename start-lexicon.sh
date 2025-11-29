#!/bin/bash
# Lexicon Server Startup Script
# Loads .env file and starts the server with environment variables

cd "$(dirname "$0")"

# Load environment variables from .env file
if [ -f .env ]; then
    echo "Loading environment variables from .env..."
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
    echo "✓ Environment variables loaded"
    echo "  YTDLP_COOKIES_PATH: $YTDLP_COOKIES_PATH"
else
    echo "Warning: .env file not found!"
fi

# Start the server
echo ""
echo "Starting Lexicon Server..."
./gradlew bootRun
