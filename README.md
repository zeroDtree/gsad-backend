# GSAD Backend

Spring Boot backend for the GPU Server Access Dashboard (GSAD).

## Quick start (mock stack)

From this directory:

```bash
cp .env.example .env   # if present; otherwise set DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET, AGENT_PSK
docker compose up --build
```

Optional periodic GPU agent reports:

```bash
docker compose --profile gpu-server-report-mock up --build
```

Services:

| Service | Port | Role |
|---------|------|------|
| `backend` | 8080 | GSAD REST API |
| `account-provision-mock` | — | Dev mock: pulls pending grants/revokes and completes them |
| `postgres` | 5432 | Database |
| `redis` | 6379 | Idempotency cache |
| `gpu-server-report-mock` | — | Refreshes server metrics (profile `gpu-server-report-mock`) |

Flyway seeds thirty mock servers (`gpu-mock-001` … `030`) on first boot.

## Frontend integration

Point the frontend dev proxy at `http://localhost:8080` (`npm run dev` in `frontend/`).

Expected flow:

1. `GET /api/public/servers` — lists mock GPU nodes with `id`
2. `POST /api/applications` with `{ serverId, purpose, requestedDays, requestedStartAt }`
3. gsad stores Linux username + password; `account-provision-mock` completes provision → status `ACTIVE` with SSH fields

API contract: [frontend/api.md](../../frontend/api.md)

Account provision API (internal): [agent-provision.md](./agent-provision.md)

## Configuration

| Variable | Description |
|----------|-------------|
| `AGENT_PSK` | Required header `X-Agent-PSK` for internal agent/provisioner APIs |

## Tests

```bash
./mvnw test
```

## Switching to production

1. **Account provision**: Implement an external service per [agent-provision.md](./agent-provision.md); disable or remove `account-provision-mock`.
2. **GPU metrics**: Deploy real [gpu-server-report](../../gpu-server-report/); optional `gpu-server-report-mock` profile for dev.
3. Populate `t_server.ssh_host` for SSH display when provisioner omits `serverIp`.
