# GSAD (GPU Server Access Dashboard) Backend

Spring Boot backend for GSAD.

## Quick start (development)

From this directory:

```bash
cp .env.example .env
proxy_on   # if your network needs a local HTTP proxy
# Docker Desktop: also set the same proxy under Settings → Proxies (build pulls use the daemon)
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile mock up --build
```

| Service | Port (dev override) | Role |
|---------|---------------------|------|
| `backend` | 8080 | GSAD REST API (`SPRING_PROFILES_ACTIVE=dev`) |
| `account-provision-mock` | — | Profile `mock`: auto grant/revoke |
| `postgres` | 5432 | Database |
| `redis` | 6379 | Idempotency cache |
| `gpu-server-report-mock` | — | Profile `mock`: periodic GPU metrics reports |

Flyway **dev** seeds: admin user + 30 mock servers (`gpu-mock-001` … `030`).  
After Flyway layout changes: `docker compose down -v` then re-up.

Frontend (separate terminal):

```bash
cd ../../frontend && npm run dev
```

## Production (Docker full stack)

Central host runs postgres, redis, backend, and frontend. **Traefik** (external) terminates TLS and routes traffic.

Prerequisites: Traefik on `${TRAEFIK_NETWORK}`, DNS A record for `${GSAD_PUBLIC_HOST}`, and `certificatesresolvers.letsencrypt` configured in Traefik.

```bash
cp .env.example .env
# Set SPRING_PROFILES_ACTIVE=prod, strong secrets, GSAD_PUBLIC_HOST, TRAEFIK_NETWORK
proxy_on   # optional: enable local HTTP proxy before image pulls
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up -d --build
```

See [docs/production-deploy.md](docs/production-deploy.md) for GPU agents, security, and backups.

## Frontend integration

- **Dev**: Vite proxy → `http://localhost:8080`
- **Prod**: same-origin via Traefik (`https://<host>/` + `/api/`)

Expected flow:

1. `POST /api/auth/register` or `POST /api/auth/login` — obtain JWT
2. `GET /api/servers` — list GPU nodes (`id` is the `serverId` for applications)
3. `POST /api/applications` with `{ serverId, purpose, requestedDays, requestedStartAt }` (optional `sshPassword`)
4. GPU `account-provisioner` completes grant → status `ACTIVE`

Public API contract: [frontend/openapi/openapi.json](../../frontend/openapi/openapi.json)

## Configuration

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod` |
| `GSAD_PUBLIC_HOST` | Public hostname for Traefik routing (prod) |
| `TRAEFIK_NETWORK` | External Docker network where Traefik runs (prod) |
| `AGENT_PSK` | Header `X-Agent-PSK` for internal APIs |
| `JWT_SECRET` | JWT signing key (≥32 chars in prod) |
| `DB_PASSWORD` | PostgreSQL password |
| `REDIS_PASSWORD` | Redis password |
| `CORS_ALLOWED_ORIGINS` | Optional prod CORS (comma-separated) |

## Switching to production

1. **Central stack**: `docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up` (no mock profiles).
2. **Account provision**: Deploy [account-provisioner](../../account-provisioner/) on each GPU host.
3. **GPU metrics**: Deploy [gpu-server-report](../../gpu-server-report/) on each GPU host.
4. Register real `server_id` values via report API; set `t_server.ssh_host` when needed.
