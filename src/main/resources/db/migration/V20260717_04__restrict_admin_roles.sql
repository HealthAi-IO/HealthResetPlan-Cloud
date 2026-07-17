SET NAMES utf8mb4;

DELETE s
FROM admin_session s
JOIN admin_account a ON a.id = s.admin_id
WHERE a.role_code IN ('support', 'auditor');

UPDATE admin_account
SET status = 0,
    deleted_at = NOW(3)
WHERE role_code IN ('support', 'auditor')
  AND deleted_at IS NULL;

UPDATE admin_role
SET name = '管理员',
    permissions = '*',
    updated_at = NOW(3)
WHERE code = 'operator';

DELETE FROM admin_role
WHERE code IN ('support', 'auditor');
