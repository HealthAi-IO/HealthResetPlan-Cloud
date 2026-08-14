ALTER TABLE user_account
    ADD COLUMN cancellation_requested_at DATETIME(3) NULL AFTER updated_at,
    ADD KEY idx_account_cancellation (status, cancellation_requested_at);
