-- ============================================================
-- V2: Add indexes
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_application_user_id
    ON t_application(user_id);

CREATE INDEX IF NOT EXISTS idx_application_audit_status
    ON t_application(audit_status);

CREATE INDEX IF NOT EXISTS idx_application_updated_at
    ON t_application(updated_at DESC);
