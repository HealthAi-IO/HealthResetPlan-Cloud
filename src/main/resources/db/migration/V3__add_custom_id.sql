-- ============================================================================
-- V3：用户展示编号
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE user_account
    ADD COLUMN custom_id VARCHAR(32) NOT NULL DEFAULT '' AFTER user_id;
