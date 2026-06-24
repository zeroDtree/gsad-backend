-- ============================================================
-- V1: Initial schema
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id               BIGSERIAL       PRIMARY KEY,
    email            VARCHAR(255)    NOT NULL UNIQUE,
    password         VARCHAR(255)    NOT NULL,
    roles            VARCHAR(255)    NOT NULL DEFAULT '',
    linux_username   VARCHAR(32)     NOT NULL UNIQUE,
    status           VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    cohort           VARCHAR(16),
    display_name     VARCHAR(64),
    student_id       VARCHAR(32)     UNIQUE,
    notes            TEXT,
    label            VARCHAR(64),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_server (
    id                 BIGSERIAL       PRIMARY KEY,
    server_id          VARCHAR(255)    NOT NULL UNIQUE,
    ssh_host           VARCHAR(255),
    resource_level     VARCHAR(255),
    status             VARCHAR(20)     NOT NULL DEFAULT 'OFFLINE',
    last_reported_at   TIMESTAMPTZ,
    metrics_json       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_application (
    id                   VARCHAR(64)     PRIMARY KEY,
    user_id              BIGINT          NOT NULL REFERENCES t_user(id),
    user_email           VARCHAR(255)    NOT NULL,
    server_id            VARCHAR(255)    NOT NULL,
    resource_level       VARCHAR(255),
    audit_status         VARCHAR(20)     NOT NULL DEFAULT 'APPROVED',
    comment              TEXT,
    idempotency_key      VARCHAR(64),
    server_ip            VARCHAR(255),
    ssh_username         VARCHAR(255),
    initial_password     VARCHAR(255),
    ssh_password_plain   VARCHAR(255),
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_application_user_idempotency
    ON t_application (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
