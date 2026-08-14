UPDATE app_release
SET package_size_mb = 14.29,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE platform = 'windows'
  AND channel = 'official'
  AND version_name = '1.0.12'
  AND version_code = 13
  AND deleted_at IS NULL;

UPDATE app_release
SET package_size_mb = 74.81,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE platform = 'android'
  AND channel = 'official'
  AND version_name = '1.0.12'
  AND version_code = 13
  AND deleted_at IS NULL;
