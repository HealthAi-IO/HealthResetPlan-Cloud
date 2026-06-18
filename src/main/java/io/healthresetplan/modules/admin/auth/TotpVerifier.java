package io.healthresetplan.modules.admin.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
public class TotpVerifier {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[20];
        SECURE_RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public boolean verify(String secret, String code) {
        if (secret == null || secret.isBlank()) return true;
        if (code == null || !code.matches("\\d{6}")) return false;

        long counter = System.currentTimeMillis() / 30_000L;
        for (long offset = -1; offset <= 1; offset++) {
            if (generate(secret, counter + offset).equals(code)) return true;
        }
        return false;
    }

    private String generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int value = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", value % 1_000_000);
        } catch (GeneralSecurityException exception) {
            return "";
        }
    }

    private byte[] decodeBase32(String secret) {
        String normalized = secret.replace("=", "").replace(" ", "").toUpperCase();
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(character);
            if (value < 0) continue;
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8 && index < output.length) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output;
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte current : bytes) {
            buffer = (buffer << 8) | (current & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return result.toString();
    }
}
