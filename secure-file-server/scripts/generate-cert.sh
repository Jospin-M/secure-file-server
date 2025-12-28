#!/bin/env bash
set -e 

CERT_DIR="/app/certs"
KEY_FILE="$CERT_DIR/server.key"
KEYSTORE="$CERT_DIR/keystore.p12"
CERT_FILE="$CERT_DIR/server.crt"

ALIAS="https-server"
KEYSTORE_PASSWORD="changeit"

# create cert directory if missing
mkdir -p "$CERT_DIR"

# exit early if certs already exist
if [i "$KEYSTORE"]; then
    echo "Certificates already exist. Skipping generation."
    exit 0
fi

echo "Generating TLS certificates..."

keytool -genkeypair \
    -alias server \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE" \
    -validity 365 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -dname "CN=localhost, OU=Dev, O=SecureFileServer, L=Local, ST=Local, C=CA"

echo "Keystore generated successfully."