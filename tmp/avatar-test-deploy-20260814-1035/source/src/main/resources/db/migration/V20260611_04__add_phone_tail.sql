ALTER TABLE user_account
  ADD COLUMN phone_tail VARCHAR(4) NOT NULL DEFAULT '' COMMENT '手机号后4位，用于后台管理区分用户'
  AFTER custom_id;

CREATE INDEX idx_user_account_phone_tail ON user_account(phone_tail);

UPDATE user_account
SET phone_tail = RIGHT(REPLACE(REPLACE(REPLACE(custom_id, ' ', ''), '-', ''), '+', ''), 4)
WHERE phone_tail = ''
  AND custom_id REGEXP '^[0-9+ -]{4,32}$';

UPDATE user_account
SET custom_id = ''
WHERE custom_id REGEXP '^[0-9+ -]{4,32}$';
