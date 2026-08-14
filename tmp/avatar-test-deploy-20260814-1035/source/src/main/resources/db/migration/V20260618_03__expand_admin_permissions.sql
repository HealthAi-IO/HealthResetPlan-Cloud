SET NAMES utf8mb4;

UPDATE admin_role
SET permissions = 'user:read,vip:read,order:read,platform:read,release:read,release:write,plan:read,plan:write,ai:read,ai:write,reminder:read,feedback:read,feedback:write'
WHERE code = 'operator';

UPDATE admin_role
SET permissions = 'audit:read,user:read,vip:read,order:read,platform:read,release:read,plan:read,ai:read,reminder:read,feedback:read'
WHERE code = 'auditor';

