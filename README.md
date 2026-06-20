# GSAD Backend

Spring Boot API for GPU Server Access Dashboard. Public API spec (dev): `/v3/api-docs`, Swagger UI `/swagger-ui.html` (disabled in `prod` profile).

## Stack

Spring Boot 4 / Java 21 · PostgreSQL 16 · Redis (idempotency) · Flyway · JWT + `X-Agent-PSK`

## Public API

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register`, `/api/auth/login` | none |
| GET | `/api/servers` | JWT |
| POST | `/api/applications` | JWT (+ optional `Idempotency-Key`) |
| DELETE | `/api/applications/{id}` | JWT — cancel (`APPROVED`) or revoke access (`ACTIVE`) |
| GET | `/api/applications/mine` | JWT |

Create body: `serverId`, optional `sshPassword`.

## Internal API (`X-Agent-PSK`)

Agents pull tasks; gsad does not call agents outbound.

| Path | Purpose |
|------|---------|
| `POST /api/internal/servers/provision/pending` | `{ "serverId" }` → `pendingGrants[]`, `pendingRevokes[]` |
| `POST /api/internal/servers/report` | GPU metrics upsert; response `data: null` |
| `POST /api/internal/servers/provision/complete` | Grant callback (`applicationId`, `serverId`, `success`, `serverIp?`) |
| `POST /api/internal/servers/revoke/complete` | Revoke callback |

**Credentials:** gsad owns `linuxUsername` + `password` at create; provisioner sends `serverIp` on grant complete only.

**Status:** `APPROVED` → grant → `ACTIVE` → user revoke → `REVOKING` → agent revoke → `REVOKED`. User cancel before grant → `CANCELLED`. Failures → `FAILED_GRANT` / `FAILED_REVOKE`.

Agent env `AGENT_SERVER_ID` must match `t_server.server_id`.

## Schema (key tables)

- **`t_server`** — `server_id`, `ssh_host`, `resource_level`, `status`, `metrics_json`, `last_reported_at`
- **`t_application`** — `server_id`, `audit_status`, `server_ip`, `ssh_username`, credentials

## Config

| Env | Required |
|-----|----------|
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | yes |
| `REDIS_HOST`, `REDIS_PASSWORD` | yes |
| `JWT_SECRET` (≥32 chars) | yes |
| `AGENT_PSK` | yes |

## Flyway

| Location | Profile | Content |
|----------|---------|---------|
| `db/migration/` V1–V2 | all | Schema (manual revoke model in V1) |
| `db/migration-dev/` V3–V4 | dev | Admin user + `gpu-mock-001..030` |

## Dev / prod

```bash
cp .env.example .env   # edit secrets; set SPRING_PROFILES_ACTIVE=prod for production
./mvnw test

# Dev + mocks (API on http://localhost:${BACKEND_PORT:-8080})
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile mock up

# Prod (no host port; expose via your ingress/LB)
docker compose up -d
```

GPU agents implement the Internal API (`X-Agent-PSK`); see table above.

If dev port bind fails, set `BACKEND_PORT` in `.env` (e.g. `18080`) or stop other stacks using 8080.

Production: route `/api` to `backend:8080` via your Traefik/nginx; block `/api/internal` at the edge.
