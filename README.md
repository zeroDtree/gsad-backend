# GSAD (GPU Server Access Dashboard) Backend

Spring Boot backend for GSAD.

## Quick start (mock stack)

From this directory:

```bash
cp .env.example .env   # if present; otherwise set DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET, AGENT_PSK
docker compose down -v   # required after Flyway squash if you have an existing volume
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

1. `POST /api/auth/register` or `POST /api/auth/login` — obtain JWT
2. `GET /api/servers` — list GPU nodes (`id` is the `serverId` for applications)
3. `POST /api/applications` with `{ serverId, purpose, requestedDays, requestedStartAt }` (optional `sshPassword`)
4. `account-provision-mock` completes provision → status `ACTIVE` with SSH fields

Public API contract: [frontend/api.md](../../frontend/api.md)

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/design.md](docs/design.md) | Backend architecture, schema, APIs |
| [docs/agent-provision.md](docs/agent-provision.md) | Internal provision/report contract |
| [docs/flowchar.md](docs/flowchar.md) | Docker compose startup flow |

## Configuration

| Variable | Description |
|----------|-------------|
| `AGENT_PSK` | Header `X-Agent-PSK` for internal agent/provisioner APIs |
| `JWT_SECRET` | JWT signing key (≥32 chars) |
| `DB_PASSWORD` | PostgreSQL password |
| `REDIS_PASSWORD` | Redis password |

## Tests

```bash
./mvnw test
```

## Switching to production

1. **Account provision**: Deploy [account-provisioner](../../account-provisioner/) on each GPU host (`git clone --recursive`); disable or remove `account-provision-mock`.
2. **GPU metrics**: Deploy real [gpu-server-report](../../gpu-server-report/); optional `gpu-server-report-mock` profile for dev.
3. Populate `t_server.ssh_host` for SSH display when provisioner omits `serverIp`.
