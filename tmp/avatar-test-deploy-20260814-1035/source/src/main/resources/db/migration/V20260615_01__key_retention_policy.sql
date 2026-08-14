ALTER TABLE user_key_meta
    ADD COLUMN last_used_at DATETIME(3) NULL AFTER backed_up_at,
    ADD COLUMN retention_started_at DATETIME(3) NULL AFTER last_used_at,
    ADD COLUMN retention_until DATETIME(3) NULL AFTER retention_started_at,
    ADD COLUMN purge_status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active / retaining / purged' AFTER retention_until,
    ADD COLUMN purged_at DATETIME(3) NULL AFTER purge_status,
    ADD KEY idx_user_key_meta_public_finger (public_finger),
    ADD KEY idx_user_key_meta_retention (purge_status, retention_until);

ALTER TABLE sync_record
    ADD COLUMN key_fingerprint VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'UMK fingerprint, not the key itself' AFTER user_id,
    ADD KEY idx_sync_record_key_since (key_fingerprint, server_updated_at);
