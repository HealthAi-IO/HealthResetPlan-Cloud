SET NAMES utf8mb4;

ALTER TABLE user_session
    ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT '' AFTER device_id,
    ADD COLUMN app_version VARCHAR(32) NOT NULL DEFAULT '' AFTER platform,
    ADD COLUMN channel VARCHAR(32) NOT NULL DEFAULT '' AFTER app_version;
