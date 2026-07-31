SET NAMES utf8mb4;

UPDATE app_release
SET status = 0
WHERE platform IN ('windows', 'android')
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
)
VALUES
(
    'windows',
    'official',
    '1.0.11',
    12,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-%E5%AE%89%E8%A3%85%E7%89%88-1.0.11.exe',
    20.39,
    '1.0.7',
    '优化首次使用、健康档案选择、AI 计划后台生成、提醒和应用菜单，并完善账号与数据安全。',
    1,
    NOW(3)
),
(
    'android',
    'official',
    '1.0.11',
    12,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.11.apk',
    75.70,
    '1.0.7',
    '优化首次使用、健康档案选择、AI 计划后台生成、提醒和应用菜单，并完善账号与数据安全。',
    1,
    NOW(3)
);
