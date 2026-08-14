SET NAMES utf8mb4;

UPDATE app_release
SET status = 0
WHERE platform = 'windows'
  AND channel = 'official'
  AND deleted_at IS NULL;

UPDATE app_release
SET version_code = 11,
    release_stage = 'release',
    is_force_update = 0,
    rollout_percent = 100,
    package_url = 'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-%E5%AE%89%E8%A3%85%E7%89%88-1.0.10.exe',
    package_size_mb = 20.61,
    min_supported_version = '1.0.7',
    release_notes = '修复 Web 与 Windows 中文显示，统一客户端功能，并优化提醒规则与云同步。',
    status = 1,
    released_at = NOW(3)
WHERE platform = 'windows'
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
    'windows',
    'official',
    '1.0.10',
    11,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-%E5%AE%89%E8%A3%85%E7%89%88-1.0.10.exe',
    20.61,
    '1.0.7',
    '修复 Web 与 Windows 中文显示，统一客户端功能，并优化提醒规则与云同步。',
    1,
    NOW(3)
WHERE NOT EXISTS (
    SELECT 1
    FROM app_release
    WHERE platform = 'windows'
      AND channel = 'official'
      AND version_name = '1.0.10'
      AND deleted_at IS NULL
);
