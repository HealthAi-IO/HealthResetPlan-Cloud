CREATE TABLE IF NOT EXISTS web_push_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    endpoint_hash CHAR(64) NOT NULL,
    endpoint_cipher LONGTEXT NOT NULL,
    endpoint_nonce VARCHAR(32) NOT NULL,
    endpoint_key_version INT NOT NULL,
    p256dh_cipher TEXT NOT NULL,
    p256dh_nonce VARCHAR(32) NOT NULL,
    p256dh_key_version INT NOT NULL,
    auth_cipher TEXT NOT NULL,
    auth_nonce VARCHAR(32) NOT NULL,
    auth_key_version INT NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    status TINYINT NOT NULL DEFAULT 1,
    failure_count INT NOT NULL DEFAULT 0,
    last_success_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_web_push_user_device (user_id, device_id),
    UNIQUE KEY uk_web_push_endpoint_hash (endpoint_hash),
    KEY idx_web_push_active (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_push_delivery (
    occurrence_key CHAR(64) NOT NULL,
    subscription_id BIGINT NOT NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (occurrence_key),
    KEY idx_web_push_delivery_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
