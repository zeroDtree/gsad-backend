# Agent account provision API (gsad ↔ external provisioner)

This document defines the **internal HTTP contract** between gsad and an external **account provision service** (your implementation runs `pkgs/isolation` or equivalent on GPU hosts). gsad does **not** call the provisioner outbound; the provisioner **pulls** work when reporting server metrics.

Authentication for all `/api/internal/**` routes: header `X-Agent-PSK: <AGENT_PSK>` (see `AgentPskFilter`).

---

## Credential ownership

| Field | Owner | When |
|-------|-------|------|
| `linuxUsername` | **gsad** | Application create |
| `password` | **gsad** | Application create (user optional `sshPassword`, else generated) |
| `serverIp` | provisioner reports | `provision/complete` (fallback: `t_server.ssh_host`) |

The provisioner must **not** generate or alter usernames/passwords. It applies the credentials from `pendingGrants` when creating Linux accounts.

---

## Status flow

```
POST /api/applications → APPROVED (credentials stored, pending provision)
provision/complete success → ACTIVE (password moved to initial_password, plain cleared)
provision/complete failure → FAILED_GRANT
ACTIVE + expire_at passed → listed in pendingRevokes
revoke/complete success → EXPIRED
revoke/complete failure → FAILED_REVOKE
```

---

## 1. `POST /api/internal/servers/report`

Existing metrics report. **Response** now includes pending provision tasks.

**Request body:** unchanged (`ServerReportRequest` — hostname, resourceLevel, summary, gpus).

**Response `data`:**

```json
{
  "pendingGrants": [
    {
      "applicationId": "app-abc12345",
      "email": "user@example.com",
      "serverId": "gpu-mock-004",
      "resourceLevel": "H100",
      "linuxUsername": "user",
      "password": "gsad-supplied-secret"
    }
  ],
  "pendingRevokes": [
    {
      "applicationId": "app-def67890",
      "linuxUsername": "user"
    }
  ]
}
```

**Selection rules:**

- `pendingGrants`: `audit_status = APPROVED` and `server_id` matches `deriveServerId(hostname)`
- `pendingRevokes`: `audit_status = ACTIVE`, `expire_at < now()`, same `server_id`

`deriveServerId`: strip trailing `.internal` from hostname (e.g. `gpu-mock-001.internal` → `gpu-mock-001`).

---

## 2. `POST /api/internal/servers/provision/complete`

Provisioner reports account creation result. **Do not send password or username** — gsad already stored them.

**Request body:**

```json
{
  "applicationId": "app-abc12345",
  "hostname": "gpu-mock-004.internal",
  "success": true,
  "serverIp": "10.0.1.5",
  "errorMessage": null
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `applicationId` | yes | |
| `hostname` | yes | Must match application's `server_id` |
| `success` | yes | |
| `serverIp` | if success | SSH reachability; required if `t_server.ssh_host` unset |
| `errorMessage` | on failure | Stored in application comment |

**On success:** gsad sets `ACTIVE`, copies stored plain password to `initial_password`, clears `ssh_password_plain`, sets `server_ip`.

---

## 3. `POST /api/internal/servers/revoke/complete`

**Request body:**

```json
{
  "applicationId": "app-def67890",
  "hostname": "gpu-mock-004.internal",
  "success": true,
  "errorMessage": null
}
```

**On success:** `audit_status` → `EXPIRED`.

---

## Dev mock

[`dev/account-provision-mock/`](dev/account-provision-mock/) polls report for mock hostnames and auto-completes grants/revokes without running real shell commands.

```bash
docker compose up --build
```

Environment: `GSAD_API_URL`, `AGENT_PSK`, `PROVISION_POLL_INTERVAL`, `MOCK_SERVER_COUNT`.

---

## Production provisioner (out of repo)

Your service should:

1. Periodically `POST .../report` per managed GPU host (or combine with your metrics agent).
2. For each `pendingGrant`, run local account setup with **exact** `linuxUsername` + `password`.
3. Call `provision/complete` with `success` and `serverIp`.
4. For each `pendingRevoke`, remove the Linux user, then `revoke/complete`.

Username CSV mapping will be resolved **inside gsad** at application create time (future); provisioner receives final `linuxUsername` in pending tasks.
