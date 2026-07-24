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
        '1.0.5',
        6,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-1.0.5.zip',
        14.45,
        '1.0.4',
        '增加当前登录账号展示，完善可选密码、安全退出、短信验证注销及登录安全保护。',
        1,
        NOW(3)
    ),
    (
        'android',
        'official',
        '1.0.5',
        6,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.5.apk',
        64.73,
        '1.0.4',
        '增加当前登录账号展示，完善可选密码、安全退出、短信验证注销及登录安全保护。',
        1,
        NOW(3)
    );
