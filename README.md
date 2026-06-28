# GSAD Backend

Spring Boot API for GPU Server Access Dashboard. Public API spec (dev): `/v3/api-docs`, Swagger UI `/swagger-ui.html` (disabled in `prod` profile).

## Stack

Spring Boot 4 / Java 21 · PostgreSQL 16 · Redis (idempotency) · Flyway · JWT + per-server agent HMAC (`X-Agent-Server-Id`, `X-Agent-PSK`)

## Public API

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/login` | none |
| POST | `/api/auth/change-password` | JWT |
| GET | `/api/servers` | JWT |
| POST | `/api/applications` | JWT (+ optional `Idempotency-Key`) |
| DELETE | `/api/applications/{id}` | JWT — cancel (`APPROVED`) or revoke access (`ACTIVE`) |
| GET | `/api/applications/mine` | JWT |

Create body: `serverId`, optional `sshPassword`.

## Admin API

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/admin/users` | JWT (admin) |
| PATCH | `/api/admin/users/{id}` | JWT (admin) |
| POST | `/api/admin/users/{id}/reset-password` | JWT (admin) |
| DELETE | `/api/admin/users/{id}` | JWT (admin); query `revokeSsh` |
| POST | `/api/admin/users/bulk-disable` | JWT (admin) |
| POST | `/api/admin/users/bulk-enable` | JWT (admin) |
| POST | `/api/admin/users/bulk-delete` | JWT (admin) |
| POST | `/api/admin/users/import` | JWT (admin); multipart CSV |
| POST | `/api/admin/servers/import` | JWT (admin); multipart CSV — required column `server_id`; optional `ssh_host`, `resource_level`; `agent_psk` ignored |

## Internal API (agent HMAC auth)

Agents pull tasks; gsad does not call agents outbound. Each request requires:

- Header `X-Agent-Server-Id`: must match JSON `serverId`
- Header `X-Agent-PSK`: `HMAC-SHA256(serverId, AGENT_MASTER_SECRET)` (hex)

Do not deploy `AGENT_MASTER_SECRET` on GPU hosts.

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
| `AGENT_MASTER_SECRET` (≥32 chars) | yes |
| `BACKEND_AGENT_BIND` | prod — loopback, RFC1918, or IP in `BACKEND_AGENT_VPN_CIDRS` |
| `BACKEND_AGENT_VPN_CIDRS` | prod when bind is overlay VPN (comma-separated CIDRs, e.g. `100.67.0.0/16`) |

## Flyway

| Location | Profile | Content |
|----------|---------|---------|
| `db/migration/` V1–V2 | all | Schema (manual revoke model in V1) |
| `db/migration-dev/` V3–V4 | dev | Admin user + `gpu-mock-001..100` |

Prod has **no seed data**.

## Dev / prod

```bash
cp .env.example .env   # edit secrets; set SPRING_PROFILES_ACTIVE=prod for production
./mvnw test
./mvnw spring-boot:run
```

GPU agents implement the Internal API (see table above).

Production: route `/api` to `backend:8080` via your Traefik/nginx; block `/api/internal` at the edge.
