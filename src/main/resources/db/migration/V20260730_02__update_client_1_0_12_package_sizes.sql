UPDATE app_release
SET package_size_mb = 15.93
WHERE platform = 'windows'
  AND channel = 'official'
  AND version_name = '1.0.12'
  AND version_code = 13
  AND deleted_at IS NULL;

UPDATE app_release
SET package_size_mb = 68.86
WHERE platform = 'android'
  AND channel = 'official'
  AND version_name = '1.0.12'
  AND version_code = 13
  AND deleted_at IS NULL;
