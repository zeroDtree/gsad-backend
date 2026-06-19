# Production deployment (GSAD central stack)

Deploy the **central** GSAD stack on one host with Docker Compose. GPU agents run separately on each GPU machine.

## Architecture

| Host | Containers |
|------|------------|
| Central | postgres, redis, backend, frontend (+ external Traefik) |
| Each GPU | account-provisioner, gpu-server-report |

Public users hit **Traefik :443** → static frontend + `/api/*` (JWT).  
`/api/internal/*` is **blocked at Traefik** (403); GPU agents call `backend:8080` on the internal Docker network or the host private IP.

## Prerequisites

- Traefik running on Docker network `${TRAEFIK_NETWORK}` (e.g. `netbird`) with:
  - `providers.docker.network=${TRAEFIK_NETWORK}`
  - `certificatesresolvers.letsencrypt` (name must match compose labels)
  - HTTP → HTTPS redirect on entrypoint `web`
- DNS A record: `${GSAD_PUBLIC_HOST}` → server public IP (DNS-01 challenge if using Let's Encrypt)

## 1. Central stack (production)

```bash
cd backend/gsad
cp .env.example .env
# Edit .env: strong DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET (>=32), AGENT_PSK (>=16)
# Set SPRING_PROFILES_ACTIVE=prod, GSAD_PUBLIC_HOST, TRAEFIK_NETWORK

proxy_on   # if your network needs a local HTTP proxy
# Docker Desktop: Settings → Proxies (image pulls during `docker build` use the daemon, not only the shell)

docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up -d --build
```

Verify:

```bash
curl -sf "https://${GSAD_PUBLIC_HOST}/"
curl -sf -o /dev/null -w "%{http_code}\n" -X POST \
  "https://${GSAD_PUBLIC_HOST}/api/internal/servers/report"   # expect 403
```

Backend health (internal):

```bash
docker compose exec backend curl -sf http://localhost:8080/actuator/health
```

## 2. Register real GPU servers

Prod Flyway runs **schema only** (V1–V2). No mock servers are seeded.

Register servers via agent report API (from GPU host or admin tooling):

```bash
curl -X POST http://<backend-internal>:8080/api/internal/servers/report \
  -H "Content-Type: application/json" \
  -H "X-Agent-PSK: $AGENT_PSK" \
  -d '{
    "serverId": "gpu-node-01",
    "resourceLevel": "H100",
    "summary": { "gpuCount": 8, "avgUtil": 0.1, "avgMemUsedMb": 1024 },
    "gpus": []
  }'
```

Optional: set `t_server.ssh_host` for SSH display when provisioner omits `serverIp` on grant complete.

## 3. GPU host agents

Deploy on **each** GPU machine (not on the central compose host):

- [account-provisioner](../../../account-provisioner/) — polls `POST /api/internal/servers/provision/pending`, runs grant/revoke, callbacks with `serverId`
- [gpu-server-report](../../../gpu-server-report/) — `POST /api/internal/servers/report`

Example environment:

| Variable | Value |
|----------|-------|
| `GSAD_API_URL` | `http://<central-private-ip>:8080` (direct to backend, not public Traefik) |
| `AGENT_PSK` | Same as central `.env` |
| `SERVER_ID` | Stable id matching `t_server.server_id` / applications |

Internal API contract: [agent-provision.md](agent-provision.md).

## 4. Development stack (reference)

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile mock up --build
# Frontend: npm run dev in frontend/ (proxies /api to localhost:8080)
```

Requires `SPRING_PROFILES_ACTIVE=dev` (default) for Flyway dev seeds (admin + 30 mock GPUs).

## 5. Operations

| Task | Command |
|------|---------|
| Logs | `docker compose logs -f backend` |
| Health | `GET /actuator/health` (backend, internal) |
| DB backup | `./deploy/scripts/backup-postgres.sh` |
| Restore volume | `docker compose down -v` (destructive) |

### Database backup

`./deploy/scripts/backup-postgres.sh` dumps PostgreSQL via `pg_dump`, gzip-compresses, verifies integrity (`gzip -t`), then prunes:

- Files older than **30 days** (`RETENTION_DAYS`)
- Oldest files first while directory total exceeds **500MB** (`MAX_TOTAL_MB`)

If a single backup exceeds `MAX_TOTAL_MB`, the script fails without removing existing history.

Optional environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKUP_DIR` | `./backups` | Output directory |
| `RETENTION_DAYS` | `30` | Max age of retained backups |
| `MAX_TOTAL_MB` | `500` | Max combined size of all retained backups |

Schedule with cron (log stdout/stderr):

```bash
0 3 * * * /path/to/backend/gsad/deploy/scripts/backup-postgres.sh >> /var/log/gsad-backup.log 2>&1
```

Back up `.env` secrets separately (not included in DB dumps).
