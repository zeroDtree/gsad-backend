"""Dev mock for external account-provision service (agent-provision.md)."""

from __future__ import annotations

import os
import time

import requests

GSAD_API_URL = os.environ.get("GSAD_API_URL", "http://backend:8080").rstrip("/")
AGENT_PSK = os.environ.get("AGENT_PSK", "change-me-in-production")
POLL_INTERVAL = max(5, int(os.environ.get("PROVISION_POLL_INTERVAL", "10")))
MOCK_SERVER_COUNT = max(1, int(os.environ.get("MOCK_SERVER_COUNT", "30")))


def headers() -> dict[str, str]:
    return {
        "Content-Type": "application/json",
        "X-Agent-PSK": AGENT_PSK,
    }


def hostname_for(index: int) -> str:
    return f"gpu-mock-{index:03d}.internal"


def minimal_report_body(hostname: str) -> dict:
    return {
        "hostname": hostname,
        "resourceLevel": "H100",
        "summary": {"gpuCount": 1, "avgUtil": 0.1, "avgMemUsedMb": 1024},
        "gpus": [
            {
                "index": 0,
                "name": "NVIDIA H100",
                "avgUtil": 0.1,
                "memUsedMb": 1024,
                "memTotalMb": 81920,
            }
        ],
    }


def server_ip_for(server_id: str) -> str:
    suffix = server_id.rsplit("-", 1)[-1]
    try:
        n = int(suffix)
    except ValueError:
        n = 1
    return f"10.0.{(n // 250) + 1}.{((n % 250) + 1)}"


def post_report(hostname: str) -> dict | None:
    url = f"{GSAD_API_URL}/api/internal/servers/report"
    resp = requests.post(url, json=minimal_report_body(hostname), headers=headers(), timeout=30)
    resp.raise_for_status()
    payload = resp.json()
    return payload.get("data")


def complete_provision(task: dict, hostname: str) -> None:
    url = f"{GSAD_API_URL}/api/internal/servers/provision/complete"
    body = {
        "applicationId": task["applicationId"],
        "hostname": hostname,
        "success": True,
        "serverIp": server_ip_for(task["serverId"]),
        "errorMessage": None,
    }
    resp = requests.post(url, json=body, headers=headers(), timeout=30)
    resp.raise_for_status()


def complete_revoke(task: dict, hostname: str) -> None:
    url = f"{GSAD_API_URL}/api/internal/servers/revoke/complete"
    body = {
        "applicationId": task["applicationId"],
        "hostname": hostname,
        "success": True,
        "errorMessage": None,
    }
    resp = requests.post(url, json=body, headers=headers(), timeout=30)
    resp.raise_for_status()


def poll_once() -> None:
    for i in range(1, MOCK_SERVER_COUNT + 1):
        hostname = hostname_for(i)
        try:
            data = post_report(hostname)
        except requests.RequestException as exc:
            print(f"WARN report failed for {hostname}: {exc}", flush=True)
            continue
        if not data:
            continue
        for grant in data.get("pendingGrants") or []:
            try:
                complete_provision(grant, hostname)
                print(
                    f"INFO provision complete app={grant.get('applicationId')} "
                    f"user={grant.get('linuxUsername')}",
                    flush=True,
                )
            except requests.RequestException as exc:
                print(f"ERROR provision complete failed: {exc}", flush=True)
        for revoke in data.get("pendingRevokes") or []:
            try:
                complete_revoke(revoke, hostname)
                print(
                    f"INFO revoke complete app={revoke.get('applicationId')} "
                    f"user={revoke.get('linuxUsername')}",
                    flush=True,
                )
            except requests.RequestException as exc:
                print(f"ERROR revoke complete failed: {exc}", flush=True)


def main() -> None:
    print(
        f"account-provision-mock polling gsad={GSAD_API_URL} "
        f"servers=1..{MOCK_SERVER_COUNT} interval={POLL_INTERVAL}s",
        flush=True,
    )
    while True:
        poll_once()
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
