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
        '1.0.6',
        7,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/windows/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Windows-%E5%AE%89%E8%A3%85%E7%89%88-1.0.6.exe',
        12.57,
        '1.0.5',
        '升级 Windows 专业桌面界面，增加四套跨端主题，并提供可卸载的正式安装程序。',
        1,
        NOW(3)
    ),
    (
        'android',
        'official',
        '1.0.6',
        7,
        'release',
        0,
        100,
        'https://jkcqplan.com/downloads/android/%E5%81%A5%E5%BA%B7%E9%87%8D%E5%90%AF%E8%AE%A1%E5%88%92-Android-1.0.6.apk',
        64.73,
        '1.0.5',
        '增加海洋蓝、健康绿、沉稳紫和暖橙四套主题，可在当前设备即时切换并自动记忆。',
        1,
        NOW(3)
    );
