# GSAD Backend Design

Spring Boot backend for GPU Server Access Dashboard. Public API contract: [frontend/openapi/openapi.json](../../../frontend/openapi/openapi.json).

---

## Stack

| Layer | Choice |
|-------|--------|
| Framework | Spring Boot 4.0.5, Java 21, WAR packaging |
| Data | Spring Data JPA + PostgreSQL 16 |
| Cache | Redis (idempotency keys, 24h TTL) |
| Migrations | Flyway V1–V2 (prod); + V3–V4 dev seeds |
| Security | JWT (user APIs) + `X-Agent-PSK` (internal APIs) |
| Docs | SpringDoc OpenAPI at `/swagger-ui.html` |

---

## Project layout

```
src/main/java/com/zerodtree/gsad/
├── config/          Security, JWT, Redis, Jackson, OpenAPI
├── common/          ApiResponse, PageResult, exceptions
├── security/        JWT filter, AgentPskFilter, @CurrentUserId
└── domain/
    ├── user/        auth (register, login)
    ├── server/      list servers, internal report/provision
    └── application/ create/list apps, AgentProvisionService, ExpirationScheduler
src/main/resources/db/migration/       V1–V2 schema (all profiles)
src/main/resources/db/migration-dev/   V3–V4 seeds (dev profile only)
deploy/                                backup scripts (Traefik handles TLS at the edge)
```

---

## Public API

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register` | none |
| POST | `/api/auth/login` | none |
| GET | `/api/servers` | JWT |
| POST | `/api/applications` | JWT + optional `Idempotency-Key` |
| GET | `/api/applications/mine` | JWT |

**Create application** body: `serverId`, `purpose`, `requestedDays`, `requestedStartAt`, optional `sshPassword`.

**List servers** returns `ServerVO`: `id` (= `serverId`), `resourceLevel`, `status`, `lastReportedAt`, `collectedAt`, `summary`, `gpus[]`.

**ApplicationVO** exposes `initialPassword` only when `credentialsReady` (ACTIVE + `serverIp` set).

Idempotency: Redis key `idempotent:{key}` → application id; DB unique constraint on `idempotency_key` as fallback.

---

## Internal API

Four POST routes under `/api/internal/servers/*`, authenticated with `X-Agent-PSK`. Full contract: [agent-provision.md](agent-provision.md).

| Path | Purpose |
|------|---------|
| `/provision/pending` | Pull grant/revoke tasks by `serverId` |
| `/report` | GPU metrics upsert; response `data: null` |
| `/provision/complete` | Grant result callback |
| `/revoke/complete` | Revoke result callback |

gsad does **not** call provisioners outbound. External services pull tasks and callback.

---

## Schema (current)

### `t_user`

`id`, `email` (unique), `password` (BCrypt), `roles` (comma-separated), timestamps.

### `t_server`

| Column | Notes |
|--------|-------|
| `server_id` | Unique business identifier (agent/report/provision key) |
| `ssh_host` | Optional SSH display IP |
| `resource_level`, `status` | `ONLINE` / `OFFLINE` / `MAINTENANCE` |
| `metrics_json` | Full GPU report JSONB (`summary`, `gpus`, `collectedAt`) |
| `last_reported_at` | |

### `t_application`

| Column | Notes |
|--------|-------|
| `id` | `app-` + 8 hex chars |
| `user_id`, `user_email` | |
| `server_id` | Bound GPU server |
| `resource_level`, `purpose`, `requested_days`, `requested_start_at`, `expire_at` | |
| `audit_status` | See state machine |
| `server_ip` | Set on successful grant |
| `ssh_username` | Derived from email local-part at create |
| `ssh_password_plain` | Pending until grant succeeds |
| `initial_password` | Exposed to user after ACTIVE |
| `idempotency_key` | Unique, optional |

---

## Application state machine

```
APPROVED ──provision/complete success──► ACTIVE ──revoke/complete success──► EXPIRED
         └──provision/complete failure──► FAILED_GRANT
                              ACTIVE ──revoke/complete failure──► FAILED_REVOKE
```

- Create sets `APPROVED` with credentials; no outbound call.
- Expired ACTIVE apps appear in `pendingRevokes`; `ExpirationScheduler` only logs counts.
- Revoke is pull-based via provisioner polling.

---

## Credential ownership

| Field | Owner |
|-------|-------|
| `linuxUsername` / `ssh_username` | gsad at create |
| `password` | gsad at create |
| `serverIp` | provisioner via `provision/complete`, or `t_server.ssh_host` fallback |

---

## Security

- `/api/auth/**` — public
- `/api/servers/**`, `/api/applications/**` — JWT (`Authorization: Bearer`)
- `/api/internal/**` — Spring Security `permitAll`, but `AgentPskFilter` requires valid PSK

JWT payload: `sub` (email), `userId`, `roles`, `exp` (default 7 days).

---

## Configuration

| Env var | Property | Required |
|---------|----------|----------|
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | `spring.datasource.*` | yes |
| `REDIS_HOST`, `REDIS_PASSWORD` | `spring.data.redis.*` | yes |
| `JWT_SECRET` | `jwt.secret` | yes (≥32 chars) |
| `AGENT_PSK` | `agent.psk` | yes |

See [../src/main/resources/application.properties](../src/main/resources/application.properties).

---

## Flyway migrations

| Location | Version | Summary | Profile |
|----------|---------|---------|---------|
| `db/migration/` | V1 | Initial schema | all |
| `db/migration/` | V2 | Indexes | all |
| `db/migration-dev/` | V3 | Seed admin user | `dev` only |
| `db/migration-dev/` | V4 | Seed `gpu-mock-001..030` | `dev` only |

`application-dev.properties` adds `classpath:db/migration-dev` to Flyway locations.

---

## Deployment

Docker Compose runs each service in its own container. See [production-deploy.md](production-deploy.md).

| Profile | Services |
|---------|----------|
| default + `docker-compose.dev.yml` | postgres, redis, backend (dev), host ports |
| `mock` | account-provision-mock, gpu-server-report-mock |
| `prod` | frontend + Traefik labels (`docker-compose.prod.yml`); no DB ports on host |

Prod Traefik blocks `/api/internal/` on :443; agents call backend :8080 on the private network.

---

## Tests

```bash
./mvnw test
```

Unit tests cover application service, JWT, exception handler. Integration tests use Testcontainers (PostgreSQL).

---

## Production notes

1. Central stack: `docker compose --profile prod up` — see [production-deploy.md](production-deploy.md).
2. Deploy [account-provisioner](../../../account-provisioner/) per GPU host.
3. Deploy [gpu-server-report](../../../gpu-server-report/) per GPU host.
4. Set `t_server.ssh_host` when provisioner omits `serverIp` in complete callback.

Startup flow: [flowchar.md](flowchar.md).
