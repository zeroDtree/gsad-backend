-- ============================================================
-- V3: Seed initial admin user (dev/test only)
-- Password: Admin@123456  (BCrypt hash below)
-- ============================================================

INSERT INTO t_user (email, password, roles, linux_username, status, display_name)
VALUES ('admin@gsad.local',
        '$2a$10$6WjNp1CrhQzl.YB.d.7PIeU9OypzxV8rNJ59KtztNM.WzxUX5hbB2',
        'admin',
        'admin',
        'ACTIVE',
        'Admin')
ON CONFLICT (email) DO NOTHING;
