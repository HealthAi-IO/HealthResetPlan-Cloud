-- ============================================================================
-- 健康重启计划（HealthResetPlan）数据库初始化脚本
-- 版本：V1
-- 编码：utf8mb4 / utf8mb4_0900_ai_ci
--
-- 核心约定：
--   1. 所有敏感字段以 `<name>_cipher / <name>_iv / <name>_tag` 三元组存储；
--      服务端不持有用户主密钥（UMK），无法解密。
--   2. 所有表均含 created_at / updated_at / deleted_at / version。
--   3. 可同步表附加：device_id / client_updated_at / server_updated_at。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. 用户与认证
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_account (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL COMMENT '业务主键，UUID',
    nickname        VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '昵称',
    avatar_url      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '头像',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用 -1注销',
    has_cloud_sync  TINYINT         NOT NULL DEFAULT 0 COMMENT '是否启用云同步',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户';

CREATE TABLE IF NOT EXISTS user_credential (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    cred_type       VARCHAR(16)     NOT NULL COMMENT 'phone/email/wechat',
    identifier_hash VARCHAR(128)    NOT NULL COMMENT '手机号/邮箱的 SHA-256 hash',
    secret_hash     VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '密码 hash 或 OAuth openid',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cred (cred_type, identifier_hash),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录凭据';

CREATE TABLE IF NOT EXISTS user_device (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(64)     NOT NULL,
    device_id         VARCHAR(64)     NOT NULL COMMENT '客户端生成的唯一 ID',
    platform          VARCHAR(16)     NOT NULL COMMENT 'ios/android/macos/windows/web/wechat',
    app_version       VARCHAR(32)     NOT NULL DEFAULT '',
    push_token        VARCHAR(512)    NOT NULL DEFAULT '',
    last_active_at    DATETIME(3)     NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备';

CREATE TABLE IF NOT EXISTS user_session (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    device_id       VARCHAR(64)     NOT NULL,
    refresh_token   VARCHAR(255)    NOT NULL,
    ip              VARCHAR(64)     NOT NULL DEFAULT '',
    user_agent      VARCHAR(255)    NOT NULL DEFAULT '',
    expires_at      DATETIME(3)     NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_token (refresh_token),
    KEY idx_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话/刷新 Token';

CREATE TABLE IF NOT EXISTS user_key_meta (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    public_finger   VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '公开的密钥指纹（如 sha256(public part)）',
    backup_method   VARCHAR(32)     NOT NULL DEFAULT '' COMMENT 'mnemonic/keystore',
    backed_up       TINYINT         NOT NULL DEFAULT 0,
    backed_up_at    DATETIME(3)     NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主密钥元数据（不含密钥本身）';

-- ============================================================================
-- 2. 用户档案（敏感字段加密）
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_profile (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    real_name_cipher   TEXT            NULL,
    real_name_iv       VARCHAR(32)     NULL,
    real_name_tag      VARCHAR(32)     NULL,
    phone_cipher       TEXT            NULL,
    phone_iv           VARCHAR(32)     NULL,
    phone_tag          VARCHAR(32)     NULL,
    email_cipher       TEXT            NULL,
    email_iv           VARCHAR(32)     NULL,
    email_tag          VARCHAR(32)     NULL,
    gender             VARCHAR(16)     NOT NULL DEFAULT 'unknown',
    birthday_cipher    TEXT            NULL,
    birthday_iv        VARCHAR(32)     NULL,
    birthday_tag       VARCHAR(32)     NULL,
    height_cipher      TEXT            NULL,
    height_iv          VARCHAR(32)     NULL,
    height_tag         VARCHAR(32)     NULL,
    weight_cipher      TEXT            NULL,
    weight_iv          VARCHAR(32)     NULL,
    weight_tag         VARCHAR(32)     NULL,
    medical_history_cipher TEXT        NULL,
    medical_history_iv     VARCHAR(32) NULL,
    medical_history_tag    VARCHAR(32) NULL,
    medications_cipher TEXT            NULL,
    medications_iv     VARCHAR(32)     NULL,
    medications_tag    VARCHAR(32)     NULL,
    alg                VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    device_id          VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at  DATETIME(3)     NULL,
    server_updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id),
    KEY idx_server_updated_at (server_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户档案（敏感字段端到端加密）';

-- ============================================================================
-- 3. 健康指标（血压、血脂、血糖、体重等）
-- ============================================================================

CREATE TABLE IF NOT EXISTS health_indicator (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    client_id          VARCHAR(64)     NOT NULL COMMENT '客户端生成的 UUID，用于幂等',
    type               VARCHAR(32)     NOT NULL COMMENT 'bp/lipid/glucose/weight/heart_rate/...',
    payload_cipher     LONGTEXT        NOT NULL,
    payload_iv         VARCHAR(32)     NOT NULL,
    payload_tag        VARCHAR(32)     NOT NULL,
    alg                VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    source             VARCHAR(32)     NOT NULL DEFAULT 'manual',
    measured_at        DATETIME(3)     NOT NULL,
    device_id          VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at  DATETIME(3)     NULL,
    server_updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_client (user_id, client_id),
    KEY idx_user_type_time (user_id, type, measured_at),
    KEY idx_server_updated_at (server_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康指标（端到端加密）';

-- ============================================================================
-- 4. 检查报告
-- ============================================================================

CREATE TABLE IF NOT EXISTS health_report (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             VARCHAR(64)     NOT NULL,
    client_id           VARCHAR(64)     NOT NULL,
    image_oss_key       VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '加密图像 OSS Key',
    image_wrapped_dek   TEXT            NULL COMMENT 'UMK 包裹的 DEK',
    image_dek_iv        VARCHAR(32)     NULL,
    image_dek_tag       VARCHAR(32)     NULL,
    ocr_text_cipher     LONGTEXT        NULL,
    ocr_text_iv         VARCHAR(32)     NULL,
    ocr_text_tag        VARCHAR(32)     NULL,
    structured_cipher   LONGTEXT        NULL,
    structured_iv       VARCHAR(32)     NULL,
    structured_tag      VARCHAR(32)     NULL,
    summary_cipher      TEXT            NULL,
    summary_iv          VARCHAR(32)     NULL,
    summary_tag         VARCHAR(32)     NULL,
    alg                 VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    report_time         DATETIME(3)     NULL,
    device_id           VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at   DATETIME(3)     NULL,
    server_updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at          DATETIME(3)     NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_client (user_id, client_id),
    KEY idx_user_time (user_id, report_time),
    KEY idx_server_updated_at (server_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查报告（端到端加密）';

-- ============================================================================
-- 5. AI 计划
-- ============================================================================

CREATE TABLE IF NOT EXISTS plan_record (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    client_id          VARCHAR(64)     NOT NULL,
    plan_type          VARCHAR(16)     NOT NULL COMMENT 'meal/exercise/medicine/composite',
    plan_date          DATE            NOT NULL,
    payload_cipher     LONGTEXT        NOT NULL,
    payload_iv         VARCHAR(32)     NOT NULL,
    payload_tag        VARCHAR(32)     NOT NULL,
    alg                VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    ai_provider        VARCHAR(32)     NOT NULL DEFAULT '',
    ai_model           VARCHAR(64)     NOT NULL DEFAULT '',
    device_id          VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at  DATETIME(3)     NULL,
    server_updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_client (user_id, client_id),
    KEY idx_user_type_date (user_id, plan_type, plan_date),
    KEY idx_server_updated_at (server_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 个性化计划（端到端加密）';

CREATE TABLE IF NOT EXISTS ai_conversation (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    session_id         VARCHAR(64)     NOT NULL,
    ai_provider        VARCHAR(32)     NOT NULL,
    ai_model           VARCHAR(64)     NOT NULL,
    prompt_cipher      LONGTEXT        NOT NULL,
    prompt_iv          VARCHAR(32)     NOT NULL,
    prompt_tag         VARCHAR(32)     NOT NULL,
    response_cipher    LONGTEXT        NOT NULL,
    response_iv        VARCHAR(32)     NOT NULL,
    response_tag       VARCHAR(32)     NOT NULL,
    alg                VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    prompt_tokens      INT             NOT NULL DEFAULT 0,
    completion_tokens  INT             NOT NULL DEFAULT 0,
    total_tokens       INT             NOT NULL DEFAULT 0,
    latency_ms         INT             NOT NULL DEFAULT 0,
    trace_id           VARCHAR(64)     NOT NULL DEFAULT '',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_session (user_id, session_id),
    KEY idx_provider_model (ai_provider, ai_model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话（端到端加密）';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code               VARCHAR(64)     NOT NULL,
    name               VARCHAR(128)    NOT NULL,
    content            LONGTEXT        NOT NULL,
    description        VARCHAR(255)    NOT NULL DEFAULT '',
    version            BIGINT          NOT NULL DEFAULT 0,
    status             TINYINT         NOT NULL DEFAULT 1,
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 提示词模板（运营维护）';

CREATE TABLE IF NOT EXISTS ai_provider_config (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider        VARCHAR(32)     NOT NULL,
    base_url        VARCHAR(255)    NOT NULL,
    model           VARCHAR(64)     NOT NULL,
    api_key_cipher  TEXT            NOT NULL COMMENT '后台加密存储',
    api_key_iv      VARCHAR(32)     NOT NULL,
    api_key_tag     VARCHAR(32)     NOT NULL,
    weight          INT             NOT NULL DEFAULT 100 COMMENT '灰度权重',
    status          TINYINT         NOT NULL DEFAULT 1,
    qps_limit       INT             NOT NULL DEFAULT 100,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_model (provider, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 厂商 / 模型配置';

-- ============================================================================
-- 6. 提醒与打卡
-- ============================================================================

CREATE TABLE IF NOT EXISTS reminder_rule (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    type               VARCHAR(16)     NOT NULL COMMENT 'meal/exercise/medicine/weight',
    cron_expr          VARCHAR(64)     NOT NULL DEFAULT '',
    payload_cipher     LONGTEXT        NULL,
    payload_iv         VARCHAR(32)     NULL,
    payload_tag        VARCHAR(32)     NULL,
    channel            VARCHAR(32)     NOT NULL DEFAULT 'local',
    status             TINYINT         NOT NULL DEFAULT 1,
    device_id          VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at  DATETIME(3)     NULL,
    server_updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒规则';

CREATE TABLE IF NOT EXISTS reminder_event (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    rule_id            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    type               VARCHAR(16)     NOT NULL,
    remind_at          DATETIME(3)     NOT NULL,
    channel            VARCHAR(32)     NOT NULL DEFAULT 'local',
    status             VARCHAR(16)     NOT NULL DEFAULT 'pending',
    sent_at            DATETIME(3)     NULL,
    read_at            DATETIME(3)     NULL,
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, remind_at),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次提醒事件';

CREATE TABLE IF NOT EXISTS clock_record (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)     NOT NULL,
    client_id          VARCHAR(64)     NOT NULL,
    type               VARCHAR(16)     NOT NULL COMMENT 'meal/exercise/medicine/weight',
    clock_at           DATETIME(3)     NOT NULL,
    status             VARCHAR(16)     NOT NULL DEFAULT 'done',
    note_cipher        TEXT            NULL,
    note_iv            VARCHAR(32)     NULL,
    note_tag           VARCHAR(32)     NULL,
    photo_oss_key      VARCHAR(255)    NOT NULL DEFAULT '',
    photo_wrapped_dek  TEXT            NULL,
    photo_dek_iv       VARCHAR(32)     NULL,
    photo_dek_tag      VARCHAR(32)     NULL,
    location_cipher    TEXT            NULL,
    location_iv        VARCHAR(32)     NULL,
    location_tag       VARCHAR(32)     NULL,
    alg                VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    device_id          VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at  DATETIME(3)     NULL,
    server_updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at         DATETIME(3)     NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_client (user_id, client_id),
    KEY idx_user_type_time (user_id, type, clock_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录（端到端加密）';

-- ============================================================================
-- 7. 可穿戴设备
-- ============================================================================

CREATE TABLE IF NOT EXISTS device_brand (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL,
    name_cn         VARCHAR(64)     NOT NULL,
    name_en         VARCHAR(64)     NOT NULL DEFAULT '',
    icon_url        VARCHAR(255)    NOT NULL DEFAULT '',
    status          TINYINT         NOT NULL DEFAULT 1,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可穿戴设备品牌';

CREATE TABLE IF NOT EXISTS device_binding (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(64)     NOT NULL,
    client_id         VARCHAR(64)     NOT NULL,
    brand_code        VARCHAR(32)     NOT NULL,
    model             VARCHAR(64)     NOT NULL DEFAULT '',
    serial_cipher     TEXT            NULL,
    serial_iv         VARCHAR(32)     NULL,
    serial_tag        VARCHAR(32)     NULL,
    alg               VARCHAR(32)     NOT NULL DEFAULT 'aes-256-gcm:v1',
    paired_at         DATETIME(3)     NULL,
    last_active_at    DATETIME(3)     NULL,
    status            TINYINT         NOT NULL DEFAULT 1,
    device_id         VARCHAR(64)     NOT NULL DEFAULT '',
    client_updated_at DATETIME(3)     NULL,
    server_updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3)     NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_client (user_id, client_id),
    KEY idx_user_brand (user_id, brand_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与可穿戴设备绑定关系';

-- ============================================================================
-- 8. 系统 / 后台
-- ============================================================================

CREATE TABLE IF NOT EXISTS admin_account (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    nickname        VARCHAR(64)     NOT NULL DEFAULT '',
    role_code       VARCHAR(32)     NOT NULL DEFAULT 'operator',
    totp_secret     VARCHAR(64)     NOT NULL DEFAULT '',
    status          TINYINT         NOT NULL DEFAULT 1,
    last_login_at   DATETIME(3)     NULL,
    last_login_ip   VARCHAR(64)     NOT NULL DEFAULT '',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号';

CREATE TABLE IF NOT EXISTS admin_role (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    permissions     TEXT            NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_type      VARCHAR(16)     NOT NULL COMMENT 'user/admin/system',
    actor_id        VARCHAR(64)     NOT NULL,
    action          VARCHAR(64)     NOT NULL,
    target          VARCHAR(255)    NOT NULL DEFAULT '',
    ip              VARCHAR(64)     NOT NULL DEFAULT '',
    user_agent      VARCHAR(255)    NOT NULL DEFAULT '',
    detail          TEXT            NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_actor (actor_type, actor_id),
    KEY idx_action (action),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

CREATE TABLE IF NOT EXISTS feedback (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    category        VARCHAR(32)     NOT NULL,
    content         TEXT            NOT NULL,
    contact         VARCHAR(64)     NOT NULL DEFAULT '',
    status          TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈';

CREATE TABLE IF NOT EXISTS sys_config (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cfg_key         VARCHAR(64)     NOT NULL,
    cfg_value       TEXT            NOT NULL,
    description     VARCHAR(255)    NOT NULL DEFAULT '',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_key (cfg_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- ============================================================================
-- 9. 初始化数据
-- ============================================================================

INSERT INTO admin_role (code, name, permissions) VALUES
    ('super_admin', '超级管理员', '*'),
    ('operator',    '运营',     'user:read,plan:write,ai:read,feedback:write'),
    ('auditor',     '审计',     'audit:read,user:read,feedback:read')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO device_brand (code, name_cn, name_en) VALUES
    ('xiaomi',  '小米',     'Xiaomi'),
    ('huawei',  '华为',     'Huawei'),
    ('apple',   '苹果',     'Apple'),
    ('google',  '谷歌',     'Google'),
    ('omron',   '欧姆龙',   'Omron'),
    ('yolanda', '云麦',     'Yolanda')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

SET FOREIGN_KEY_CHECKS = 1;
