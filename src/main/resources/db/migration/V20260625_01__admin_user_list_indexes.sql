-- Keep admin user list responsive as user/session/sync data grows.
CREATE INDEX idx_user_account_deleted_created ON user_account(deleted_at, created_at);
CREATE INDEX idx_user_account_deleted_custom_id ON user_account(deleted_at, custom_id);
CREATE INDEX idx_user_session_user_created ON user_session(user_id, created_at);
CREATE INDEX idx_user_subscription_user_status_expires ON user_subscription(user_id, status, expires_at);
CREATE INDEX idx_sync_record_user_table_deleted ON sync_record(user_id, table_name, deleted_at);
