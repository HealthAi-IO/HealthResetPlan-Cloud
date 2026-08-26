CREATE TABLE IF NOT EXISTS ai_growth_trial (
    user_id       VARCHAR(64) NOT NULL,
    starts_at     DATETIME(3) NOT NULL,
    expires_at    DATETIME(3) NOT NULL,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    KEY idx_ai_growth_trial_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 成长体验期';

INSERT IGNORE INTO ai_growth_trial (user_id, starts_at, expires_at)
SELECT user_id, CURRENT_TIMESTAMP(3), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)
FROM user_account
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ai_feature_usage (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(64) NOT NULL,
    feature_code   VARCHAR(32) NOT NULL,
    period_key     VARCHAR(32) NOT NULL,
    benefit_source VARCHAR(16) NOT NULL,
    used_count     INT         NOT NULL DEFAULT 0,
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_feature_usage_period (user_id, feature_code, period_key, benefit_source),
    KEY idx_ai_feature_usage_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 功能周期权益使用记录';

ALTER TABLE user_vip_subscription
    ADD COLUMN benefit_used TINYINT NOT NULL DEFAULT 0 AFTER remaining_credit;
