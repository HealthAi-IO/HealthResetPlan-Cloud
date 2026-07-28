SET NAMES utf8mb4;

UPDATE app_release
SET status = 0
WHERE platform = 'android'
  AND channel = 'official'
  AND deleted_at IS NULL;

UPDATE app_release
SET version_code = 11,
    release_stage = 'release',
    is_force_update = 0,
    rollout_percent = 100,
    package_url = 'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.10.apk',
    package_size_mb = 74.71,
    min_supported_version = '1.0.7',
    release_notes = '账号数据改为登录后自动在线同步，移除本地存储、手动云同步和客户端加密设置。',
    status = 1,
    released_at = NOW(3)
WHERE platform = 'android'
  AND channel = 'official'
  AND version_name = '1.0.10'
  AND deleted_at IS NULL;

INSERT INTO app_release (
    platform,
    channel,
    version_name,
    version_code,
    release_stage,
    is_force_update,
    rollout_percent,
    package_url,
    package_size_mb,
    min_supported_version,
    release_notes,
    status,
    released_at
)
SELECT
    'android',
    'official',
    '1.0.10',
    11,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.10.apk',
    74.71,
    '1.0.7',
    '账号数据改为登录后自动在线同步，移除本地存储、手动云同步和客户端加密设置。',
    1,
    NOW(3)
WHERE NOT EXISTS (
    SELECT 1
    FROM app_release
    WHERE platform = 'android'
      AND channel = 'official'
      AND version_name = '1.0.10'
      AND deleted_at IS NULL
);
