CREATE TABLE IF NOT EXISTS health_content (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type                  VARCHAR(16)     NOT NULL COMMENT 'article/card/qa/todo',
    title                 VARCHAR(160)    NOT NULL,
    summary               VARCHAR(500)    NOT NULL DEFAULT '',
    cover_url             VARCHAR(1024)   NOT NULL DEFAULT '',
    cover_prompt          VARCHAR(1000)   NOT NULL DEFAULT '',
    body_html             LONGTEXT        NULL,
    content_json          JSON            NULL,
    status                VARCHAR(24)     NOT NULL DEFAULT 'draft' COMMENT 'draft/pending_review/published/offline',
    source_type           VARCHAR(16)     NOT NULL DEFAULT 'manual' COMMENT 'manual/ai',
    ai_provider           VARCHAR(64)     NOT NULL DEFAULT '',
    ai_model              VARCHAR(128)    NOT NULL DEFAULT '',
    content_hash          CHAR(64)        NOT NULL,
    scheduled_publish_at  DATETIME(3)     NULL,
    published_at          DATETIME(3)     NULL,
    created_by            BIGINT          NULL,
    reviewed_by           BIGINT          NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version               BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_content_hash (content_hash),
    KEY idx_health_content_list (status, published_at, id),
    KEY idx_health_content_type (type, status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康资讯主表';

CREATE TABLE IF NOT EXISTS health_content_read (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(64)     NOT NULL,
    content_id     BIGINT UNSIGNED NOT NULL,
    first_read_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_read_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_content_read (user_id, content_id),
    KEY idx_health_content_read_user (user_id, last_read_at),
    CONSTRAINT fk_health_content_read_content FOREIGN KEY (content_id)
        REFERENCES health_content (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资讯阅读记录';

CREATE TABLE IF NOT EXISTS ai_content_task (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name              VARCHAR(100)    NOT NULL,
    content_type      VARCHAR(16)     NOT NULL DEFAULT 'card',
    topic             VARCHAR(500)    NOT NULL DEFAULT '日常饮食、运动、睡眠与体重管理科普',
    schedule_type     VARCHAR(16)     NOT NULL DEFAULT 'weekly' COMMENT 'daily/weekly',
    day_of_week       TINYINT         NOT NULL DEFAULT 1 COMMENT '1周一至7周日',
    publish_time      TIME            NOT NULL DEFAULT '09:00:00',
    publish_mode      VARCHAR(16)     NOT NULL DEFAULT 'auto' COMMENT 'review/auto',
    preferred_provider VARCHAR(64)    NOT NULL DEFAULT '',
    image_enabled     TINYINT         NOT NULL DEFAULT 0,
    enabled           TINYINT         NOT NULL DEFAULT 1,
    next_run_at       DATETIME(3)     NULL,
    last_run_at       DATETIME(3)     NULL,
    last_result       VARCHAR(24)     NOT NULL DEFAULT '',
    last_error        VARCHAR(1000)   NOT NULL DEFAULT '',
    created_by        BIGINT          NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_content_task_due (enabled, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI资讯生成任务配置';

CREATE TABLE IF NOT EXISTS site_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(64)     NOT NULL,
    content_id  BIGINT UNSIGNED NULL,
    type        VARCHAR(24)     NOT NULL DEFAULT 'content_published',
    title       VARCHAR(160)    NOT NULL,
    body        VARCHAR(500)    NOT NULL DEFAULT '',
    status      VARCHAR(16)     NOT NULL DEFAULT 'unread' COMMENT 'unread/read',
    read_at     DATETIME(3)     NULL,
    expires_at  DATETIME(3)     NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_site_message_user_content (user_id, content_id, type),
    KEY idx_site_message_user_list (user_id, status, created_at),
    CONSTRAINT fk_site_message_content FOREIGN KEY (content_id)
        REFERENCES health_content (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

INSERT INTO ai_content_task (
    name, content_type, topic, schedule_type, day_of_week, publish_time,
    publish_mode, preferred_provider, image_enabled, enabled, next_run_at
)
SELECT
    '每周健康科普', 'card', '日常饮食、运动、睡眠与体重管理科普',
    'weekly', 1, '09:00:00', 'auto', '', 0, 1,
    CASE
        WHEN TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL ((9 - DAYOFWEEK(CURDATE())) % 7) DAY), '09:00:00') > NOW(3)
            THEN TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL ((9 - DAYOFWEEK(CURDATE())) % 7) DAY), '09:00:00')
        ELSE TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL (((9 - DAYOFWEEK(CURDATE())) % 7) + 7) DAY), '09:00:00')
    END
WHERE NOT EXISTS (SELECT 1 FROM ai_content_task);

UPDATE admin_role
SET permissions = CASE
    WHEN permissions = '*' OR FIND_IN_SET('content:read', permissions) > 0 THEN permissions
    ELSE CONCAT_WS(',', permissions, 'content:read', 'content:write', 'content:publish')
END
WHERE code IN ('super_admin', 'admin');
