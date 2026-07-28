SET NAMES utf8mb4;

DELIMITER $$
CREATE PROCEDURE delete_test_data_if_table_exists(IN target_table VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = target_table
    ) THEN
        SET @delete_sql = CONCAT('DELETE FROM `', target_table, '`');
        PREPARE delete_statement FROM @delete_sql;
        EXECUTE delete_statement;
        DEALLOCATE PREPARE delete_statement;
    END IF;
END$$
DELIMITER ;

CALL delete_test_data_if_table_exists('ai_conversation');
CALL delete_test_data_if_table_exists('ai_user_consent');
CALL delete_test_data_if_table_exists('client_event');
CALL delete_test_data_if_table_exists('clock_record');
CALL delete_test_data_if_table_exists('feedback');
CALL delete_test_data_if_table_exists('health_indicator');
CALL delete_test_data_if_table_exists('health_report');
CALL delete_test_data_if_table_exists('device_binding');
CALL delete_test_data_if_table_exists('payment_order');
CALL delete_test_data_if_table_exists('plan_record');
CALL delete_test_data_if_table_exists('reminder_event');
CALL delete_test_data_if_table_exists('reminder_rule');
CALL delete_test_data_if_table_exists('sync_record');
CALL delete_test_data_if_table_exists('user_device');
CALL delete_test_data_if_table_exists('user_registration_consent');
CALL delete_test_data_if_table_exists('user_subscription');
CALL delete_test_data_if_table_exists('user_profile');
CALL delete_test_data_if_table_exists('user_session');
CALL delete_test_data_if_table_exists('user_key_meta');
CALL delete_test_data_if_table_exists('user_credential');
CALL delete_test_data_if_table_exists('user_account');

DROP PROCEDURE delete_test_data_if_table_exists;

DROP TABLE IF EXISTS sync_record;
DROP TABLE IF EXISTS user_key_meta;

DELIMITER $$
CREATE PROCEDURE remove_cloud_sync_feature_if_table_exists()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'membership_plan'
    ) THEN
        UPDATE membership_plan
        SET features = REPLACE(REPLACE(features, '"cloud_sync",', ''), ',"cloud_sync"', '');
    END IF;
END$$
DELIMITER ;

CALL remove_cloud_sync_feature_if_table_exists();
DROP PROCEDURE remove_cloud_sync_feature_if_table_exists;

CREATE TABLE IF NOT EXISTS user_data_state (
    user_id         VARCHAR(64)  NOT NULL,
    payload_cipher  LONGTEXT     NOT NULL,
    payload_nonce   VARCHAR(32)  NOT NULL,
    key_version     INT          NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 1,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务端加密的用户在线业务数据';
