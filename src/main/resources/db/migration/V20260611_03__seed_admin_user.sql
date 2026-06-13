SET @admin_user_id = '100000000001';
SET @admin_identifier_hash = '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918';
SET @admin_password_hash = '$2b$10$w8zjdkf9xcoMH7aaIBbZIO73yPSdcFxCrwCt/GoTrZ8M14zhV165q';

INSERT INTO user_account (
  user_id,
  custom_id,
  nickname,
  avatar_url,
  status,
  role_code,
  has_cloud_sync,
  created_at,
  updated_at,
  version
) VALUES (
  @admin_user_id,
  'admin',
  'admin',
  '',
  1,
  'admin',
  0,
  NOW(3),
  NOW(3),
  0
) ON DUPLICATE KEY UPDATE
  custom_id = VALUES(custom_id),
  nickname = VALUES(nickname),
  status = VALUES(status),
  role_code = VALUES(role_code),
  updated_at = NOW(3);

INSERT INTO user_credential (
  user_id,
  cred_type,
  identifier_hash,
  secret_hash,
  created_at,
  updated_at,
  version
) VALUES (
  @admin_user_id,
  'phone',
  @admin_identifier_hash,
  @admin_password_hash,
  NOW(3),
  NOW(3),
  0
) ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  secret_hash = VALUES(secret_hash),
  updated_at = NOW(3),
  deleted_at = NULL;
