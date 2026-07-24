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
        '1.0.4',
        5,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-1.0.4.zip',
        14.45,
        '1.0.3',
        '修复 Web AI 会话同步到 Windows SQLite 时的字段兼容问题。',
        1,
        NOW(3)
    ),
    (
        'android',
        'official',
        '1.0.4',
        5,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.4.apk',
        64.67,
        '1.0.3',
        '修复 Web AI 会话同步到 Android SQLite 时的字段兼容问题。',
        1,
        NOW(3)
    );
