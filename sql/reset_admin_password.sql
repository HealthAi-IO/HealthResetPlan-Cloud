-- 一次性脚本：把 admin 账号密码重置为 admin123
-- 用法：在任意 MySQL 客户端连接 health_reset_plan 库后执行
-- 注意：本文件位于 ./sql/ 而非 ./sql/migration/，Flyway 不会自动扫描
--      跑完一次即可，建议本地确认后从仓库里删除，避免明文密码遗留

USE health_reset_plan;

UPDATE user_credential
SET secret_hash = '$2b$10$JHT0tp9fDgUhRazZ4TxFueOqnuRAd5CQNyVYG2PmVY/aPpKd3hH2e',
    updated_at  = NOW(3),
    deleted_at  = NULL
WHERE user_id = '100000000001' AND cred_type = 'phone';

-- 同时确保账号是启用状态、role_code=admin（防止之前被误改）
UPDATE user_account
SET status     = 1,
    role_code  = 'admin',
    deleted_at = NULL,
    updated_at = NOW(3)
WHERE user_id = '100000000001';

-- 顺便清空该账号已有的会话，强制重新登录拿到新 token
DELETE FROM user_session WHERE user_id = '100000000001';
