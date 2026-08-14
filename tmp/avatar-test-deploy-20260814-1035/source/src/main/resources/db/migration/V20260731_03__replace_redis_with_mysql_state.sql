CREATE TABLE IF NOT EXISTS app_ephemeral_state (
    state_key    VARCHAR(255) NOT NULL,
    state_value  LONGTEXT     NOT NULL,
    expires_at   DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (state_key),
    KEY idx_app_ephemeral_state_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码、限流和短期缓存';
