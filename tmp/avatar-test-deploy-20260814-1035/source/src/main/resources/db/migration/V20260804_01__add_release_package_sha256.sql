ALTER TABLE app_release
    ADD COLUMN package_sha256 CHAR(64) NOT NULL DEFAULT '' AFTER package_url;
