# Production deployment (GSAD central stack)

Deploy the **central** GSAD stack on one host with Docker Compose. GPU agents run separately on each GPU machine.

## Architecture

| Host | Containers |
|------|------------|
| Central | postgres, redis, backend, frontend, nginx |
| Each GPU | account-provisioner, gpu-server-report |

Public users hit **nginx :443** → static frontend + `/api/*` (JWT).  
`/api/internal/*` is **blocked on 443**; GPU agents call `backend:8080` on the internal Docker network or the host private IP.

## 1. Central stack (production)

```bash
cd backend/gsad
cp .env.example .env
# Edit .env: strong DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET (>=32), AGENT_PSK (>=16)
# Set SPRING_PROFILES_ACTIVE=prod

proxy_on   # if your network needs a local HTTP proxy
# Docker Desktop: Settings → Proxies (image pulls during `docker build` use the daemon, not only the shell)

./deploy/certs/generate-dev-certs.sh   # or install real tls.crt / tls.key in deploy/certs/

docker compose --profile prod up -d --build
```

Verify:

```bash
curl -k https://localhost/healthz
curl -k https://localhost/api/internal/servers/report -X POST   # expect 403 from nginx
```

Backend health (from host with docker network access):

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
| `GSAD_API_URL` | `http://<central-private-ip>:8080` (direct to backend, not public nginx) |
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
| Health | `GET /actuator/health` (backend), `GET /healthz` (nginx) |
| DB backup | `./deploy/scripts/backup-postgres.sh` |
| Restore volume | `docker compose down -v` (destructive) |

Schedule backups with cron, e.g. daily `0 3 * * * /path/to/deploy/scripts/backup-postgres.sh`.

## 6. Security checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] Strong `JWT_SECRET` and `AGENT_PSK` (not example values)
- [ ] Postgres / Redis / backend **not** published on host ports (prod compose default)
- [ ] TLS certificates in `deploy/certs/`
- [ ] Firewall: only 443 public; GPU subnet → backend 8080 for agents
- [ ] Remove `account-provision-mock` profile in production
