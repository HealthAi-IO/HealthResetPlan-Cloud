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
) VALUES
    (
        'windows',
        'official',
        '1.0.3',
        4,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-1.0.3.zip',
        14.45,
        '1.0.2',
        '修复 Windows 云同步页面无法返回、跨设备报告合并和主密钥冲突提示。',
        1,
        NOW(3)
    ),
    (
        'android',
        'official',
        '1.0.3',
        4,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.3.apk',
        64.67,
        '1.0.2',
        '修复跨设备报告合并和主密钥冲突提示。',
        1,
        NOW(3)
    );
