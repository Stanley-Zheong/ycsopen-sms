-- F-1.1: bootstrap the development Web console administrator.
-- This repeatable migration is loaded only by the dev profile. It deliberately
-- preserves an existing account so upgrades never reset an operator's password.
INSERT INTO users (
    username,
    password_hash,
    real_name,
    user_type,
    status,
    failed_login_count,
    created_by
)
SELECT
    'admin',
    '$2a$10$Zfn0WBm0FBiNkDFuGgsZv.OnOUL8sBR7GdvZTEtgh9O4ssEA6QQe2',
    '系统管理员',
    'ADMIN',
    'ACTIVE',
    0,
    'flyway-dev'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);
