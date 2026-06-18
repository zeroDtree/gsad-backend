# Agent account provision API (GSAD ↔ external provisioner)

Internal HTTP contract between gsad and an external **account provision service** ([`account-provisioner`](../../../account-provisioner/) on GPU hosts). gsad does **not** call the provisioner outbound; the provisioner **pulls** pending tasks via `provision/pending`. GPU metrics use `report` separately.

All `/api/internal/**` routes require header `X-Agent-PSK: <AGENT_PSK>` (`AgentPskFilter`).

All responses use `ApiResponse { code, message, data }`. On success, `code` is `""` and `message` is `"ok"`.

---

## Credential ownership

| Field | Owner | When |
|-------|-------|------|
| `linuxUsername` | **gsad** | Application create |
| `password` | **gsad** | Application create (optional user `sshPassword`, else generated) |
| `serverIp` | provisioner | `provision/complete` (fallback: `t_server.ssh_host`) |

The provisioner must **not** generate or alter usernames/passwords.

Mock seeds use `server_id` values such as `gpu-mock-004`.

---

## Status flow

```
POST /api/applications → APPROVED (credentials stored)
provision/complete success → ACTIVE (plain password → initial_password)
provision/complete failure → FAILED_GRANT
ACTIVE + expire_at passed → pendingRevokes
revoke/complete success → EXPIRED
revoke/complete failure → FAILED_REVOKE
```

---

## 1. `POST /api/internal/servers/provision/pending`

Poll pending grant/revoke tasks. Does not update server metrics.

**Request:**

```json
{ "serverId": "gpu-mock-004" }
```

| Field | Required | Notes |
|-------|----------|-------|
| `serverId` | yes | Must match callbacks and application `server_id` |

**Response `data`:**

```json
{
  "pendingGrants": [{
    "applicationId": "app-abc12345",
    "email": "user@example.com",
    "serverId": "gpu-mock-004",
    "resourceLevel": "H100",
    "linuxUsername": "user",
    "password": "gsad-supplied-secret"
  }],
  "pendingRevokes": [{
    "applicationId": "app-def67890",
    "linuxUsername": "user"
  }]
}
```

**Selection rules:**

- `pendingGrants`: `audit_status = APPROVED`, matching `server_id`
- `pendingRevokes`: `audit_status = ACTIVE`, `expire_at < now()`, same `server_id`

---

## 2. `POST /api/internal/servers/report`

GPU metrics only. Updates `t_server` metrics and ONLINE status.

**Request** (`ServerReportRequest`): `serverId`, `resourceLevel`, optional `collectedAt`, `summary { gpuCount, avgUtil, avgMemUsedMb }`, `gpus[]`.

**Response `data`:** `null`

---

## 3. `POST /api/internal/servers/provision/complete`

Report account creation. **Do not send username or password.**

**Request:**

```json
{
  "applicationId": "app-abc12345",
  "serverId": "gpu-mock-004",
  "success": true,
  "serverIp": "10.0.1.5",
  "errorMessage": null
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `applicationId` | yes | |
| `serverId` | yes | Must match application `server_id` |
| `success` | yes | |
| `serverIp` | if success | Required when `t_server.ssh_host` is unset |
| `errorMessage` | on failure | Stored in application comment |

**Response `data`:** `null`

**On success:** status → `ACTIVE`; plain password copied to `initial_password`; `server_ip` set.

---

## 4. `POST /api/internal/servers/revoke/complete`

**Request:**

```json
{
  "applicationId": "app-def67890",
  "serverId": "gpu-mock-004",
  "success": true,
  "errorMessage": null
}
```

**Response `data`:** `null`

**On success:** status → `EXPIRED`.

---

## Dev mock

[`../dev/account-provision-mock/`](../dev/account-provision-mock/) polls `provision/pending` and auto-completes grants/revokes.

```bash
docker compose up --build
```

| Variable | Default |
|----------|---------|
| `GSAD_API_URL` | `http://backend:8080` |
| `AGENT_PSK` | from `.env` |
| `PROVISION_POLL_INTERVAL` | `10` (seconds) |
| `MOCK_SERVER_COUNT` | `30` |

---

## Production provisioner

Deploy [`account-provisioner`](../../../account-provisioner/) on each GPU host. It:

1. Polls `POST .../provision/pending` with the host `serverId`.
2. Runs `isolation/add-user.sh` with exact `linuxUsername` + `password` from each grant.
3. Calls `provision/complete` with `success` and `serverIp`.
4. Runs `isolation/remove-user.sh` for revokes, then `revoke/complete`.

See `account-provisioner/README.md` for setup.
