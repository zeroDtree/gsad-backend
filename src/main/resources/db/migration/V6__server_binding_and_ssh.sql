-- Server business id + SSH fields
ALTER TABLE t_server
    ADD COLUMN IF NOT EXISTS server_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ssh_host VARCHAR(255);

UPDATE t_server SET server_id = hostname WHERE server_id IS NULL;

ALTER TABLE t_server
    ALTER COLUMN server_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_server_server_id ON t_server (server_id);

-- Application server binding + SSH credentials
ALTER TABLE t_application
    ADD COLUMN IF NOT EXISTS server_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS server_ip VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ssh_username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS initial_password VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ssh_password_plain VARCHAR(255),
    ADD COLUMN IF NOT EXISTS password_delivered BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE t_application SET server_id = 'legacy-unknown' WHERE server_id IS NULL;

ALTER TABLE t_application
    ALTER COLUMN server_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_application_server_id ON t_application (server_id);

-- Mock GPU servers for dev/demo (skip if already present)
INSERT INTO t_server (
    hostname, server_id, ssh_host, resource_level, status,
    last_reported_at, avg_util, avg_mem_used_mb, metrics_json
)
SELECT
    'gpu-mock-001.internal',
    'gpu-mock-001',
    '10.0.0.101',
    'H100',
    'ONLINE',
    NOW(),
    0.7800,
    78336,
    '{"collectedAt":"2026-01-01T00:00:00Z","summary":{"gpuCount":8,"avgUtil":0.78,"avgMemUsedMb":78336},"gpus":[{"index":0,"name":"NVIDIA H100","avgUtil":0.82,"memUsedMb":79000,"memTotalMb":81920}]}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM t_server WHERE server_id = 'gpu-mock-001');

INSERT INTO t_server (
    hostname, server_id, ssh_host, resource_level, status,
    last_reported_at, avg_util, avg_mem_used_mb, metrics_json
)
SELECT
    'gpu-mock-002.internal',
    'gpu-mock-002',
    '10.0.0.102',
    'A100',
    'ONLINE',
    NOW(),
    0.4500,
    40960,
    '{"collectedAt":"2026-01-01T00:00:00Z","summary":{"gpuCount":4,"avgUtil":0.45,"avgMemUsedMb":40960},"gpus":[{"index":0,"name":"NVIDIA A100","avgUtil":0.45,"memUsedMb":40000,"memTotalMb":81920}]}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM t_server WHERE server_id = 'gpu-mock-002');

INSERT INTO t_server (
    hostname, server_id, ssh_host, resource_level, status,
    last_reported_at, avg_util, avg_mem_used_mb, metrics_json
)
SELECT
    'gpu-mock-003.internal',
    'gpu-mock-003',
    '10.0.0.103',
    'L40S',
    'ONLINE',
    NOW(),
    0.2200,
    20480,
    '{"collectedAt":"2026-01-01T00:00:00Z","summary":{"gpuCount":2,"avgUtil":0.22,"avgMemUsedMb":20480},"gpus":[{"index":0,"name":"NVIDIA L40S","avgUtil":0.22,"memUsedMb":20000,"memTotalMb":49152}]}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM t_server WHERE server_id = 'gpu-mock-003');
