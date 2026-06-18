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

Optional periodic GPU agent reports:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile mock --profile gpu-server-report-mock up --build
```

| Service | Port (dev override) | Role |
|---------|---------------------|------|
| `backend` | 8080 | GSAD REST API (`SPRING_PROFILES_ACTIVE=dev`) |
| `account-provision-mock` | — | Profile `mock`: auto grant/revoke |
| `postgres` | 5432 | Database |
| `redis` | 6379 | Idempotency cache |
| `gpu-server-report-mock` | — | Profile `gpu-server-report-mock` |

Flyway **dev** seeds: admin user + 30 mock servers (`gpu-mock-001` … `030`).  
After Flyway layout changes: `docker compose down -v` then re-up.

Frontend (separate terminal):

```bash
cd ../../frontend && npm run dev
```

## Production (Docker full stack)

Central host runs postgres, redis, backend, frontend, and nginx (each in its own container).

```bash
cp .env.example .env
# Set SPRING_PROFILES_ACTIVE=prod and strong secrets
proxy_on   # optional: enable local HTTP proxy before image pulls
./deploy/certs/generate-dev-certs.sh   # or install real certs in deploy/certs/
docker compose --profile prod up -d --build
```

See [docs/production-deploy.md](docs/production-deploy.md) for GPU agents, security, and backups.

## Frontend integration

- **Dev**: Vite proxy → `http://localhost:8080`
- **Prod**: same-origin via nginx (`https://<host>/` + `/api/`)

Expected flow:

1. `POST /api/auth/register` or `POST /api/auth/login` — obtain JWT
2. `GET /api/servers` — list GPU nodes (`id` is the `serverId` for applications)
3. `POST /api/applications` with `{ serverId, purpose, requestedDays, requestedStartAt }` (optional `sshPassword`)
4. GPU `account-provisioner` completes grant → status `ACTIVE`

Public API contract: [frontend/openapi/openapi.json](../../frontend/openapi/openapi.json)

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/design.md](docs/design.md) | Architecture, schema, APIs |
| [docs/agent-provision.md](docs/agent-provision.md) | Internal agent contract |
| [docs/production-deploy.md](docs/production-deploy.md) | Prod compose, agents, ops |
| [docs/flowchar.md](docs/flowchar.md) | Startup flow |

## Configuration

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod` |
| `AGENT_PSK` | Header `X-Agent-PSK` for internal APIs |
| `JWT_SECRET` | JWT signing key (≥32 chars in prod) |
| `DB_PASSWORD` | PostgreSQL password |
| `REDIS_PASSWORD` | Redis password |
| `CORS_ALLOWED_ORIGINS` | Optional prod CORS (comma-separated) |

## Tests

```bash
./mvnw test
```

## Switching to production

1. **Central stack**: `docker compose --profile prod up` (no mock profiles).
2. **Account provision**: Deploy [account-provisioner](../../account-provisioner/) on each GPU host.
3. **GPU metrics**: Deploy [gpu-server-report](../../gpu-server-report/) on each GPU host.
4. Register real `server_id` values via report API; set `t_server.ssh_host` when needed.
