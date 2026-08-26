CREATE TABLE IF NOT EXISTS user_vip_subscription (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)  NOT NULL,
    plan_code          VARCHAR(32)  NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'active',
    starts_at          DATETIME(3)  NOT NULL,
    expires_at         DATETIME(3)  NOT NULL,
    credit_amount      INT          NOT NULL,
    remaining_credit   INT          NOT NULL,
    payment_order_no   VARCHAR(40)  NOT NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vip_subscription_order (payment_order_no),
    KEY idx_vip_subscription_user_status (user_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 VIP 会员订阅';

UPDATE ai_credit_product
SET credit_amount = CASE code
    WHEN 'vip_month' THEN 30
    WHEN 'vip_quarter' THEN 90
    WHEN 'vip_year' THEN 360
END,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE code IN ('vip_month', 'vip_quarter', 'vip_year');
