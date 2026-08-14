SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS client_event (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    platform        VARCHAR(16)     NOT NULL,
    app_version     VARCHAR(32)     NOT NULL DEFAULT '',
    channel         VARCHAR(32)     NOT NULL DEFAULT 'official',
    event_type      VARCHAR(32)     NOT NULL,
    device_id       VARCHAR(128)    NOT NULL DEFAULT '',
    trace_id        VARCHAR(64)     NOT NULL DEFAULT '',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_client_event_platform_time (platform, created_at),
    KEY idx_client_event_type_time (event_type, created_at),
    KEY idx_client_event_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端运行质量元数据，不包含健康明文';

