-- ============================================================================
-- 健康重启计划 V2：会员付费体系
-- ============================================================================

SET NAMES utf8mb4;

-- 套餐定义（运营配置）
CREATE TABLE IF NOT EXISTS membership_plan (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL COMMENT '套餐码：monthly / yearly',
    name            VARCHAR(64)     NOT NULL,
    price_fen       INT             NOT NULL COMMENT '价格（分）',
    duration_days   INT             NOT NULL COMMENT '有效期天数',
    features        TEXT            NOT NULL COMMENT 'JSON 权益列表，如 ["cloud_sync","report_ocr"]',
    sort_order      INT             NOT NULL DEFAULT 0,
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员套餐定义';

-- 用户订阅
CREATE TABLE IF NOT EXISTS user_subscription (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          VARCHAR(64)     NOT NULL,
    plan_code        VARCHAR(32)     NOT NULL,
    status           VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active / expired / cancelled',
    starts_at        DATETIME(3)     NOT NULL,
    expires_at       DATETIME(3)     NOT NULL,
    payment_channel  VARCHAR(32)     NOT NULL DEFAULT '' COMMENT 'wechat / alipay / apple_iap / admin',
    payment_order_no VARCHAR(64)     NOT NULL DEFAULT '',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_expires_at (expires_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅记录';

-- 支付订单
CREATE TABLE IF NOT EXISTS payment_order (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(64)     NOT NULL COMMENT '平台订单号',
    user_id          VARCHAR(64)     NOT NULL,
    plan_code        VARCHAR(32)     NOT NULL,
    amount_fen       INT             NOT NULL COMMENT '实付金额（分）',
    channel          VARCHAR(32)     NOT NULL COMMENT 'wechat / alipay / apple_iap',
    channel_order_no VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '第三方订单号',
    status           VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending / paid / failed / refunded',
    callback_raw     TEXT            NULL COMMENT '原始回调报文',
    paid_at          DATETIME(3)     NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id),
    KEY idx_channel_order (channel, channel_order_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单';

-- 初始套餐数据
INSERT INTO membership_plan (code, name, price_fen, duration_days, features, sort_order) VALUES
    ('monthly', '月度会员', 1800, 31,  '["cloud_sync","report_ocr","ai_plan_unlimited"]',            1),
    ('yearly',  '年度会员', 9800, 366, '["cloud_sync","report_ocr","ai_plan_unlimited","priority_support"]', 2)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);
