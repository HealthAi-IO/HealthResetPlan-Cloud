CREATE TABLE IF NOT EXISTS user_registration_consent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    agreement_version VARCHAR(32) NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_user_registration_consent_user (user_id)
);
