# TLS certificates for edge nginx (prod compose)

Place `tls.crt` and `tls.key` in this directory before `docker compose --profile prod up`.

## Local testing (self-signed)

```bash
./generate-dev-certs.sh
```

Browsers will warn about the self-signed certificate; use `-k` with curl.

## Production

Use certificates from your CA (Let's Encrypt, internal PKI, etc.) and mount them here or update the nginx volume paths.
