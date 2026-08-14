SET NAMES utf8mb4;

UPDATE admin_role
SET permissions = REPLACE(REPLACE(REPLACE(permissions, 'feedback:read,', ''), 'feedback:write,', ''), 'feedback:export,', '');
