#!/usr/bin/env bash
# Generate a self-signed TLS cert for local prod compose testing.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$DIR"

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$DIR/tls.key" \
  -out "$DIR/tls.crt" \
  -subj "/CN=localhost/O=GSAD Dev"

echo "Wrote $DIR/tls.crt and $DIR/tls.key"
