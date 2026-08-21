package io.healthresetplan.modules.payment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PaymentCrypto {
    private PaymentCrypto() {}

    public static PrivateKey privateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception ex) {
            throw new IllegalStateException("支付商户私钥无法读取", ex);
        }
    }

    public static PublicKey publicKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            if (pem.contains("BEGIN CERTIFICATE")) {
                try (var input = Files.newInputStream(Path.of(path))) {
                    return ((X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(input)).getPublicKey();
                }
            }
            String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception ex) {
            throw new IllegalStateException("支付平台公钥无法读取", ex);
        }
    }

    public static String sign(String value, PrivateKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("支付签名失败", ex);
        }
    }

    static boolean verify(String value, String signatureValue, PublicKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (Exception ex) {
            return false;
        }
    }
}
