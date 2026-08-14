ALTER TABLE user_account
  ADD COLUMN role_code VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT 'user=普通用户 admin=后台管理员'
  AFTER status;

CREATE INDEX idx_user_account_role_code ON user_account(role_code);
