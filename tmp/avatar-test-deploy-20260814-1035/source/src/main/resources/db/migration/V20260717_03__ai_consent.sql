CREATE TABLE IF NOT EXISTS ai_user_consent (
  user_id VARCHAR(64) NOT NULL,
  policy_version VARCHAR(32) NOT NULL,
  accepted_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户云端 AI 数据处理单独同意';
