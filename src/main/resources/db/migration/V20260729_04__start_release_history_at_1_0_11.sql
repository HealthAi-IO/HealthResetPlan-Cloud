SET NAMES utf8mb4;

UPDATE app_release
SET status = 0,
    release_stage = 'paused',
    deleted_at = NOW(3)
WHERE deleted_at IS NULL
  AND NOT (
    platform IN ('android', 'windows')
    AND channel = 'official'
    AND version_name = '1.0.11'
    AND version_code = 12
  );

UPDATE app_release
SET status = 1,
    release_stage = 'release',
    rollout_percent = 100
WHERE deleted_at IS NULL
  AND platform IN ('android', 'windows')
  AND channel = 'official'
  AND version_name = '1.0.11'
  AND version_code = 12;
