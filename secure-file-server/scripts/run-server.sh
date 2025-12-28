#!/bin/env bash
set -e

CERT_DIR="/app/certs"
KEYSTORE="$CERT_DIR/keystore.p12"

echo "Starting container..."

# generate certs if missing
if [ ! -f "$KEYSTORE" ]; then
    echo "No TLS certificates found. Generating..."
    /app/generate-cert.sh
else
    echo "TLS certificates found."
fi

echo "Starting HTTPS server..."
exec java -jar /app/server.jar