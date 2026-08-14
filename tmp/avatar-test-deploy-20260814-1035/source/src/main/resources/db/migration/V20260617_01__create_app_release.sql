SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS app_release (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    platform          VARCHAR(16)     NOT NULL COMMENT 'windows/macos/android/ios/web/wechat',
    channel           VARCHAR(32)     NOT NULL DEFAULT 'official' COMMENT 'official/testflight/internal/store/channel name',
    version_name      VARCHAR(32)     NOT NULL COMMENT 'semantic version display',
    version_code      INT             NOT NULL DEFAULT 0 COMMENT 'build/version code',
    release_stage     VARCHAR(16)     NOT NULL DEFAULT 'release' COMMENT 'draft/gray/release/paused',
    is_force_update   TINYINT         NOT NULL DEFAULT 0,
    rollout_percent   INT             NOT NULL DEFAULT 100,
    package_url       VARCHAR(512)    NOT NULL DEFAULT '',
    package_size_mb   DECIMAL(10,2)   NOT NULL DEFAULT 0,
    min_supported_version VARCHAR(32) NOT NULL DEFAULT '',
    release_notes     TEXT            NOT NULL,
    status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1 active 0 archived',
    released_at       DATETIME(3)     NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3)     NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_platform_stage (platform, release_stage),
    KEY idx_channel (channel),
    KEY idx_released_at (released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多端版本发布记录';

INSERT INTO app_release (
    platform,
    channel,
    version_name,
    version_code,
    release_stage,
    is_force_update,
    rollout_percent,
    package_url,
    package_size_mb,
    min_supported_version,
    release_notes,
    status,
    released_at
) VALUES
    ('windows', 'official', '1.2.0', 120, 'release', 0, 100, 'https://download.jkcqplan.com/windows/HealthResetPlan-1.2.0.exe', 148.50, '1.0.0', '桌面端体验优化，新增离线同步恢复。', 1, DATE_SUB(NOW(3), INTERVAL 2 DAY)),
    ('macos', 'official', '1.2.0', 120, 'gray', 0, 35, 'https://download.jkcqplan.com/macos/HealthResetPlan-1.2.0.dmg', 162.30, '1.0.0', 'Apple Silicon 兼容增强，修复启动卡顿。', 1, DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    ('android', 'official', '2.3.1', 231, 'release', 1, 100, 'https://download.jkcqplan.com/android/health-reset-plan-2.3.1.apk', 86.20, '2.1.0', '会员中心重构，支持强制更新。', 1, DATE_SUB(NOW(3), INTERVAL 4 DAY)),
    ('ios', 'appstore', '2.3.1', 231, 'release', 0, 100, 'https://apps.apple.com/app/id0000000000', 0, '2.0.0', 'App Store 正式发布，优化 AI 计划生成。', 1, DATE_SUB(NOW(3), INTERVAL 3 DAY)),
    ('wechat', 'official', '1.8.0', 180, 'release', 0, 100, 'https://download.jkcqplan.com/wechat/1.8.0', 12.40, '1.6.0', '小程序端提醒链路升级。', 1, DATE_SUB(NOW(3), INTERVAL 5 DAY)),
    ('web', 'official', '1.5.2', 152, 'release', 0, 100, 'https://app.jkcqplan.com', 0, '1.4.0', 'Web 端仪表盘和账户中心升级。', 1, DATE_SUB(NOW(3), INTERVAL 6 DAY))
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);
