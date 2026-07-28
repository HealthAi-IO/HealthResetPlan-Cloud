package io.healthresetplan.common.crypto;

import io.healthresetplan.config.DataEncryptionProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataEncryptionServiceTests {

    @Test
    void encryptsAndDecryptsTextAndFilesWithAad() {
        DataEncryptionProperties properties = new DataEncryptionProperties();
        properties.setKey(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        properties.setKeyVersion(1);
        DataEncryptionService service = new DataEncryptionService(properties);
        service.initialize();

        var text = service.encryptText("健康数据", "user-data:1");
        assertEquals("健康数据", service.decryptText(
                text.ciphertext(), text.nonce(), text.keyVersion(), "user-data:1"));

        byte[] plaintext = "report-image".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = service.encryptFile(plaintext, "user-file:1:file");
        assertArrayEquals(plaintext, service.decryptFile(encrypted, "user-file:1:file"));
        assertThrows(IllegalStateException.class,
                () -> service.decryptFile(encrypted, "user-file:2:file"));
    }
}
