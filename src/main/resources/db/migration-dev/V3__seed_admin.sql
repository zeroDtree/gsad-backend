-- ============================================================
-- V3: Seed initial admin user (dev/test only)
-- Password: Admin@123456  (BCrypt hash below)
-- ============================================================

INSERT INTO t_user (email, password, roles)
VALUES ('admin@gsad.local',
        '$2a$10$7EqJtq98hPqEX7fNZaFWoOa/xP3QWF1LcuHEMl8fqFT4oR.yJJW5O',
        'admin')
ON CONFLICT (email) DO NOTHING;
