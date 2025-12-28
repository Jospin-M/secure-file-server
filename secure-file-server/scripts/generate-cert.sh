#!/usr/bin/env bash
set -e 

CERT_DIR="../certs"
KEY_FILE="$CERT_DIR/server.key"
CERT_FILE="$CERT_DIR/server.crt"
P12_FILE="$CERT_DIR/keystore.p12"
ALIAS="https-server"
PASSWORD="changeit"

mkdir -p "$CERT_DIR"

echo "Generating private key..."
openssl genrsa -out "$KEY_FILE" 2048

echo "Generating self-signed certificate..."
openssl req -new -x509 \
-key "$KEY_FILE" \
-out "$CERT_FILE" \
-days 365 \
-subj "/CN=localhost"

echo "Packaging into PKCS12 keystore..."
openssl pkcs12 -export \
-inkey "$KEY_FILE" \
-in "$CERT_FILE" \
-out "$P12_FILE" \
-name "$ALIAS" \
-passout pass:"$PASSWORD"

