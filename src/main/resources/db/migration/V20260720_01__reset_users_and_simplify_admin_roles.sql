SET NAMES utf8mb4;

DELETE FROM ai_conversation;
DELETE FROM ai_user_consent;
DELETE FROM client_event;
DELETE FROM clock_record;
DELETE FROM feedback;
DELETE FROM health_indicator;
DELETE FROM health_report;
DELETE FROM plan_record;
DELETE FROM reminder_event;
DELETE FROM reminder_rule;
DELETE FROM sync_record;
DELETE FROM user_profile;
DELETE FROM user_session;
DELETE FROM user_key_meta;
DELETE FROM user_credential;
DELETE FROM user_account;

DELETE FROM admin_session;
DELETE FROM admin_account
WHERE role_code NOT IN ('super_admin', 'admin');
DELETE FROM admin_role
WHERE code NOT IN ('super_admin', 'admin');

INSERT INTO admin_role (code, name, permissions)
VALUES
    ('super_admin', '超级管理员', '*'),
    ('admin', '管理员', '*')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    permissions = VALUES(permissions),
    updated_at = CURRENT_TIMESTAMP(3);

ALTER TABLE user_account
    ADD UNIQUE KEY uk_user_account_custom_id (custom_id);
