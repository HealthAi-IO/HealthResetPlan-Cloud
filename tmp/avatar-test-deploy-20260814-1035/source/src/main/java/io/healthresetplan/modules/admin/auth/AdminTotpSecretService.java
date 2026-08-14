package io.healthresetplan.modules.admin.auth;

import io.healthresetplan.common.crypto.DataEncryptionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminTotpSecretService {

    private final JdbcTemplate jdbc;
    private final DataEncryptionService encryption;

    public AdminTotpSecretService(JdbcTemplate jdbc, DataEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    public boolean isEnabled(Map<String, Object> admin) {
        return !text(admin.get("totp_secret_cipher")).isBlank()
                || !text(admin.get("totp_secret")).isBlank();
    }

    public String decrypt(long adminId, Map<String, Object> admin) {
        String cipher = text(admin.get("totp_secret_cipher"));
        if (cipher.isBlank()) return text(admin.get("totp_secret"));
        return encryption.decryptText(
                cipher,
                text(admin.get("totp_secret_nonce")),
                number(admin.get("totp_secret_key_version")),
                "admin-totp:" + adminId);
    }

    public int enable(long adminId, String secret) {
        DataEncryptionService.EncryptedText encrypted = encryption.encryptText(
                secret.trim().toUpperCase(), "admin-totp:" + adminId);
        return jdbc.update("""
                UPDATE admin_account
                SET totp_secret = '', totp_secret_cipher = ?,
                    totp_secret_nonce = ?, totp_secret_key_version = ?
                WHERE id = ? AND status = 1 AND deleted_at IS NULL
                  AND totp_secret = '' AND totp_secret_cipher IS NULL
                """, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), adminId);
    }

    public void disable(long adminId) {
        jdbc.update("""
                UPDATE admin_account
                SET totp_secret = '', totp_secret_cipher = NULL,
                    totp_secret_nonce = NULL, totp_secret_key_version = NULL
                WHERE id = ?
                """, adminId);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
    }
}
