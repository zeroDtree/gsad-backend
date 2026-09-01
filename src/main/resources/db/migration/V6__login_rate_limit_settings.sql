-- ============================================================
-- V5: Singleton login rate-limit settings
-- ============================================================

CREATE TABLE IF NOT EXISTS t_login_rate_limit_setting (
    id                       SMALLINT      PRIMARY KEY DEFAULT 1
                                           CHECK (id = 1),
    window_minutes           INT           NOT NULL DEFAULT 15
                                           CHECK (window_minutes BETWEEN 1 AND 1440),
    max_attempts_per_email   INT           NOT NULL DEFAULT 5
                                           CHECK (max_attempts_per_email BETWEEN 1 AND 100),
    max_attempts_per_ip      INT           NOT NULL DEFAULT 30
                                           CHECK (max_attempts_per_ip BETWEEN 1 AND 1000),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

INSERT INTO t_login_rate_limit_setting (id, window_minutes, max_attempts_per_email, max_attempts_per_ip)
VALUES (1, 15, 5, 30)
ON CONFLICT (id) DO NOTHING;
