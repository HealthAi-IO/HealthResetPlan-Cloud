ALTER TABLE feedback
    ADD COLUMN content_cipher LONGTEXT NULL AFTER content,
    ADD COLUMN content_nonce VARCHAR(32) NULL AFTER content_cipher,
    ADD COLUMN content_key_version INT NULL AFTER content_nonce,
    ADD COLUMN contact_cipher TEXT NULL AFTER contact,
    ADD COLUMN contact_nonce VARCHAR(32) NULL AFTER contact_cipher,
    ADD COLUMN contact_key_version INT NULL AFTER contact_nonce;

ALTER TABLE admin_account
    ADD COLUMN totp_secret_cipher TEXT NULL AFTER totp_secret,
    ADD COLUMN totp_secret_nonce VARCHAR(32) NULL AFTER totp_secret_cipher,
    ADD COLUMN totp_secret_key_version INT NULL AFTER totp_secret_nonce;
