-- Agent (gpu-server-report) sends free-form resource labels (e.g. GPU product name).
ALTER TABLE t_server
    ALTER COLUMN resource_level TYPE VARCHAR(255);

-- Snapshot subtree aligned with agent JSON: { collectedAt, gpus, summary }
ALTER TABLE t_server
    ADD COLUMN IF NOT EXISTS metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb;
