package io.healthresetplan.common.crypto;

import io.healthresetplan.config.DataEncryptionProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DataEncryptionService {

    private static final byte[] FILE_MAGIC = {'H', 'R', 'P', '1'};
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final DataEncryptionProperties properties;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;

    public DataEncryptionService(DataEncryptionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getKey().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY 必须是 Base64", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY 解码后必须为 32 字节");
        }
        key = new SecretKeySpec(decoded, "AES");
    }

    public EncryptedText encryptText(String plaintext, String aad) {
        requireKey();
        byte[] nonce = randomNonce();
        byte[] cipher = crypt(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), nonce, aad);
        return new EncryptedText(
                Base64.getEncoder().encodeToString(cipher),
                Base64.getEncoder().encodeToString(nonce),
                properties.getKeyVersion());
    }

    public String decryptText(String ciphertext, String nonce, int keyVersion, String aad) {
        requireKeyVersion(keyVersion);
        byte[] plain = crypt(
                Cipher.DECRYPT_MODE,
                Base64.getDecoder().decode(ciphertext),
                Base64.getDecoder().decode(nonce),
                aad);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public byte[] encryptFile(byte[] plaintext, String aad) {
        requireKey();
        byte[] nonce = randomNonce();
        byte[] cipher = crypt(Cipher.ENCRYPT_MODE, plaintext, nonce, aad);
        return ByteBuffer.allocate(FILE_MAGIC.length + Integer.BYTES + NONCE_LENGTH + cipher.length)
                .put(FILE_MAGIC)
                .putInt(properties.getKeyVersion())
                .put(nonce)
                .put(cipher)
                .array();
    }

    public byte[] decryptFile(byte[] encrypted, String aad) {
        if (encrypted == null || encrypted.length <= FILE_MAGIC.length + Integer.BYTES + NONCE_LENGTH) {
            throw new IllegalArgumentException("文件密文格式无效");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] magic = new byte[FILE_MAGIC.length];
        buffer.get(magic);
        if (!java.util.Arrays.equals(FILE_MAGIC, magic)) {
            throw new IllegalArgumentException("文件密文格式无效");
        }
        int keyVersion = buffer.getInt();
        requireKeyVersion(keyVersion);
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] cipher = new byte[buffer.remaining()];
        buffer.get(cipher);
        return crypt(Cipher.DECRYPT_MODE, cipher, nonce, aad);
    }

    private byte[] crypt(int mode, byte[] input, byte[] nonce, String aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感数据加解密失败", ex);
        }
    }

    private byte[] randomNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        return nonce;
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("缺少 DATA_ENCRYPTION_KEY");
        }
    }

    private void requireKeyVersion(int version) {
        requireKey();
        if (version != properties.getKeyVersion()) {
            throw new IllegalStateException("不支持的数据密钥版本：" + version);
        }
    }

    public record EncryptedText(String ciphertext, String nonce, int keyVersion) {
    }
}
