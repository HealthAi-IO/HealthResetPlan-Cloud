package io.healthresetplan.common.crypto;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
public class SensitiveDataMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final DataEncryptionService encryption;

    public SensitiveDataMigration(JdbcTemplate jdbc, DataEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateFeedback();
        migrateAdminTotp();
    }

    private void migrateFeedback() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, content, contact
                FROM feedback
                WHERE (content <> '' AND content_cipher IS NULL)
                   OR (contact <> '' AND contact_cipher IS NULL)
                """);
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String content = text(row.get("content"));
            String contact = text(row.get("contact"));
            if (!content.isEmpty()) {
                DataEncryptionService.EncryptedText encrypted = encryption.encryptText(
                        content, "feedback:" + id + ":content");
                jdbc.update("""
                        UPDATE feedback
                        SET content = '', content_cipher = ?, content_nonce = ?, content_key_version = ?
                        WHERE id = ?
                        """, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), id);
            }
            if (!contact.isEmpty()) {
                DataEncryptionService.EncryptedText encrypted = encryption.encryptText(
                        contact, "feedback:" + id + ":contact");
                jdbc.update("""
                        UPDATE feedback
                        SET contact = '', contact_cipher = ?, contact_nonce = ?, contact_key_version = ?
                        WHERE id = ?
                        """, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), id);
            }
        }
    }

    private void migrateAdminTotp() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, totp_secret
                FROM admin_account
                WHERE totp_secret <> '' AND totp_secret_cipher IS NULL
                """);
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            DataEncryptionService.EncryptedText encrypted = encryption.encryptText(
                    text(row.get("totp_secret")), "admin-totp:" + id);
            jdbc.update("""
                    UPDATE admin_account
                    SET totp_secret = '', totp_secret_cipher = ?,
                        totp_secret_nonce = ?, totp_secret_key_version = ?
                    WHERE id = ?
                    """, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), id);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
