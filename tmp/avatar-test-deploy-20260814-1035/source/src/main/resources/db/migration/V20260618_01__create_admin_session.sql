SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS admin_session (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_id            BIGINT UNSIGNED NOT NULL,
    refresh_token_hash  CHAR(64)        NOT NULL,
    expires_at          DATETIME(3)     NOT NULL,
    ip                  VARCHAR(64)     NOT NULL DEFAULT '',
    user_agent          VARCHAR(512)    NOT NULL DEFAULT '',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_refresh_token_hash (refresh_token_hash),
    KEY idx_admin_session_admin (admin_id),
    KEY idx_admin_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录会话';

INSERT INTO admin_account (
    username,
    password_hash,
    nickname,
    role_code,
    status,
    last_login_ip
)
SELECT
    'admin',
    credential.secret_hash,
    '超级管理员',
    'super_admin',
    1,
    ''
FROM user_credential credential
WHERE credential.user_id = '100000000001'
  AND credential.deleted_at IS NULL
LIMIT 1
ON DUPLICATE KEY UPDATE username = VALUES(username);

