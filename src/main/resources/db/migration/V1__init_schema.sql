-- ============================================================
-- V1: Initial schema
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id           BIGSERIAL       PRIMARY KEY,
    email        VARCHAR(255)    NOT NULL UNIQUE,
    password     VARCHAR(255)    NOT NULL,
    roles        VARCHAR(255)    NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_server (
    id                 BIGSERIAL       PRIMARY KEY,
    hostname           VARCHAR(255)    NOT NULL UNIQUE,
    resource_level     VARCHAR(50)     NOT NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'OFFLINE',
    last_reported_at   TIMESTAMPTZ,
    avg_util           DECIMAL(5,4),
    avg_mem_used_mb    INTEGER,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_application (
    id                   VARCHAR(64)     PRIMARY KEY,
    user_id              BIGINT          NOT NULL REFERENCES t_user(id),
    user_email           VARCHAR(255)    NOT NULL,
    resource_level       VARCHAR(50)     NOT NULL,
    purpose              VARCHAR(500)    NOT NULL,
    requested_days       INTEGER         NOT NULL CHECK (requested_days > 0),
    requested_start_at   TIMESTAMPTZ     NOT NULL,
    expire_at            TIMESTAMPTZ,
    audit_status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    comment              TEXT,
    idempotency_key      VARCHAR(64)     UNIQUE,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
