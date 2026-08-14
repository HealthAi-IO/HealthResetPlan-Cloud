SET NAMES utf8mb4;

UPDATE app_release
SET status = 0
WHERE platform = 'windows'
  AND channel = 'official'
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
) VALUES (
    'windows',
    'official',
    '1.0.2',
    3,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-1.0.2.zip',
    14.45,
    '1.0.2',
    'Windows 10/11 绿色版，支持本地数据管理与云同步。',
    1,
    NOW(3)
);
