CREATE TABLE IF NOT EXISTS ai_credit_product (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    price_fen       INT          NOT NULL,
    credit_amount   INT          NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 1,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_credit_product_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 次数包商品';

INSERT INTO ai_credit_product (code, name, price_fen, credit_amount, status, sort_order)
VALUES ('ai_20', 'AI 健康分析包', 990, 20, 1, 10),
       ('ai_60', 'AI 健康分析大容量包', 1990, 60, 1, 20)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price_fen = VALUES(price_fen),
    credit_amount = VALUES(credit_amount),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

CREATE TABLE IF NOT EXISTS ai_credit_account (
    user_id          VARCHAR(64)  NOT NULL,
    balance          INT          NOT NULL DEFAULT 0,
    granted_total    INT          NOT NULL DEFAULT 0,
    consumed_total   INT          NOT NULL DEFAULT 0,
    trial_granted    TINYINT      NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT chk_ai_credit_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 AI 次数余额';

CREATE TABLE IF NOT EXISTS payment_order (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    order_no              VARCHAR(40)   NOT NULL,
    user_id               VARCHAR(64)   NOT NULL,
    product_code          VARCHAR(32)   NOT NULL,
    product_name          VARCHAR(64)   NOT NULL,
    amount_fen            INT           NOT NULL,
    credit_amount         INT           NOT NULL,
    remaining_credit      INT           NOT NULL DEFAULT 0,
    channel               VARCHAR(16)   NOT NULL,
    channel_order_no      VARCHAR(128)  NULL,
    status                VARCHAR(24)   NOT NULL DEFAULT 'created',
    paid_at               DATETIME(3)   NULL,
    expires_at            DATETIME(3)   NOT NULL,
    refunded_at           DATETIME(3)   NULL,
    refund_amount_fen     INT           NOT NULL DEFAULT 0,
    refund_reason         VARCHAR(255)  NULL,
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_no (order_no),
    UNIQUE KEY uk_payment_channel_order (channel, channel_order_no),
    KEY idx_payment_user_created (user_id, created_at),
    KEY idx_payment_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 次数包支付订单';

CREATE TABLE IF NOT EXISTS ai_credit_ledger (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(64)   NOT NULL,
    change_amount     INT           NOT NULL,
    balance_after     INT           NOT NULL,
    reason            VARCHAR(32)   NOT NULL,
    source_order_no   VARCHAR(40)   NULL,
    feature_code      VARCHAR(64)   NULL,
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_ledger_order_reason (source_order_no, reason),
    KEY idx_credit_ledger_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 次数变更流水';

CREATE TABLE IF NOT EXISTS payment_refund (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    refund_no             VARCHAR(40)   NOT NULL,
    order_no              VARCHAR(40)   NOT NULL,
    user_id               VARCHAR(64)   NOT NULL,
    channel               VARCHAR(16)   NOT NULL,
    channel_refund_no     VARCHAR(128)  NULL,
    amount_fen            INT           NOT NULL,
    credit_amount         INT           NOT NULL,
    status                VARCHAR(24)   NOT NULL DEFAULT 'requested',
    reason                VARCHAR(255)  NOT NULL,
    reviewed_by           BIGINT        NULL,
    reviewed_at           DATETIME(3)   NULL,
    completed_at          DATETIME(3)   NULL,
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_refund_no (refund_no),
    UNIQUE KEY uk_payment_refund_order (order_no),
    KEY idx_payment_refund_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付退款申请';
