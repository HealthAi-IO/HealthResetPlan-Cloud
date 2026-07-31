SET NAMES utf8mb4;

UPDATE app_release
SET status = 0,
    release_stage = 'paused'
WHERE platform IN ('android', 'windows', 'web')
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
    '1.0.12',
    13,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-%E5%AE%89%E8%A3%85%E7%89%88-1.0.12.exe',
    21.56,
    '1.0.7',
    '统一 Android、Windows 和 Web 版本，修复启动未授权、Web 字体显示、每日任务统计与下载体验。',
    1,
    NOW(3)
),
(
    'android',
    'official',
    '1.0.12',
    13,
    'release',
    0,
    100,
    'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.12.apk',
    76.98,
    '1.0.7',
    '统一 Android、Windows 和 Web 版本，修复启动未授权、Web 字体显示、每日任务统计与下载体验。',
    1,
    NOW(3)
),
(
    'web',
    'official',
    '1.0.12',
    13,
    'release',
    0,
    100,
    'https://app.jkcqplan.com',
    0,
    '1.0.7',
    '统一 Android、Windows 和 Web 版本，修复启动未授权、Web 字体显示、每日任务统计与下载体验。',
    1,
    NOW(3)
);
