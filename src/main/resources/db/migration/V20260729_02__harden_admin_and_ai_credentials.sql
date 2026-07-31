UPDATE admin_account
SET status = 0,
    updated_at = NOW(3)
WHERE password_hash IN (
    '$2a$10$lmcjlE1bzrV/WAx9TsAo2uAMsoaMygoos/vbva6cyNIEX0FBjzqxS',
    '$2a$10$yIRsbs4egzFys0pN9hlCtulJVKPlG5QCPPAzYMzyw16yiMtYBhkWW'
);

UPDATE ai_provider_config
SET api_key_cipher = '',
    api_key_iv = '',
    api_key_tag = '',
    updated_at = NOW(3)
WHERE api_key_cipher <> ''
   OR api_key_iv <> ''
   OR api_key_tag <> '';
