-- 浏览器 UA 字符串经常超过 255（特别是 Edge / Chrome 带 brand list 时），
-- 之前会因 Data truncation 导致 user_session 插入失败，登录链路 500。
-- 这里把 user_session / audit_log 的 user_agent 列扩大到 1024。

ALTER TABLE user_session
    MODIFY COLUMN user_agent VARCHAR(1024) NOT NULL DEFAULT '';

ALTER TABLE audit_log
    MODIFY COLUMN user_agent VARCHAR(1024) NOT NULL DEFAULT '';
