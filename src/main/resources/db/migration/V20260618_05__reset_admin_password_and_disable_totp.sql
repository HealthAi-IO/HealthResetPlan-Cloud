UPDATE admin_account
SET password_hash = '$2a$10$lmcjlE1bzrV/WAx9TsAo2uAMsoaMygoos/vbva6cyNIEX0FBjzqxS',
    totp_secret = '',
    updated_at = NOW(3)
WHERE username = 'admin'
  AND deleted_at IS NULL;
