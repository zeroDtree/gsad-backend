-- ============================================================
-- V5: Per-server agent PSK (encrypted at rest by the application)
-- ============================================================

ALTER TABLE t_server ADD COLUMN IF NOT EXISTS agent_psk TEXT;
