ALTER TABLE app_release
    ADD COLUMN git_commit VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'source Git commit' AFTER package_sha256,
    ADD COLUMN backend_version VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'backend build/version' AFTER git_commit,
    ADD COLUMN migration_version VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'latest applied database migration' AFTER backend_version,
    ADD COLUMN artifact_path VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'verified build artifact path' AFTER migration_version,
    ADD COLUMN built_at DATETIME(3) NULL COMMENT 'artifact build time' AFTER artifact_path;
