SET NAMES utf8mb4;

UPDATE admin_role
SET permissions = 'user:read,user:export,analytics:read,analytics:export,feedback:read,feedback:write,feedback:export,platform:read,release:read,release:write,plan:read,plan:write,ai:read,ai:write,reminder:read'
WHERE code = 'operator';

UPDATE admin_role
SET permissions = 'audit:read,user:read,user:export,analytics:read,analytics:export,feedback:read,feedback:export,platform:read,release:read,plan:read,ai:read,reminder:read'
WHERE code = 'auditor';

INSERT INTO admin_role (code, name, permissions) VALUES
    ('support', '客服', 'feedback:read,feedback:write,feedback:export')
ON DUPLICATE KEY UPDATE name = VALUES(name), permissions = VALUES(permissions), updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO admin_account (username, password_hash, nickname, role_code, status)
VALUES
    ('admin', '$2a$10$yIRsbs4egzFys0pN9hlCtulJVKPlG5QCPPAzYMzyw16yiMtYBhkWW', '超级管理员', 'super_admin', 1),
    ('operator', '$2a$10$yIRsbs4egzFys0pN9hlCtulJVKPlG5QCPPAzYMzyw16yiMtYBhkWW', '运营', 'operator', 1),
    ('support', '$2a$10$yIRsbs4egzFys0pN9hlCtulJVKPlG5QCPPAzYMzyw16yiMtYBhkWW', '客服', 'support', 1),
    ('auditor', '$2a$10$yIRsbs4egzFys0pN9hlCtulJVKPlG5QCPPAzYMzyw16yiMtYBhkWW', '审计', 'auditor', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash), nickname = VALUES(nickname), role_code = VALUES(role_code), status = VALUES(status), totp_secret = '';
