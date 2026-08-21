package io.healthresetplan.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class WechatGateway implements PaymentGateway {

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WechatGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() { return "wechat"; }

    @Override
    public boolean enabled() {
        PaymentProperties.Wechat config = properties.getWechat();
        return config.isEnabled() && !blank(config.getAppId()) && !blank(config.getMerchantId())
                && !blank(config.getMerchantSerialNumber()) && !blank(config.getPrivateKeyPath())
                && !blank(config.getPlatformPublicKeyPath()) && !blank(config.getPlatformPublicKeyId())
                && config.getApiV3Key().length() == 32;
    }

    @Override
    public Map<String, Object> createPayment(String orderNo, String subject, int amountFen, LocalDateTime expiresAt) {
        requireEnabled();
        PaymentProperties.Wechat config = properties.getWechat();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appid", config.getAppId());
        request.put("mchid", config.getMerchantId());
        request.put("description", subject);
        request.put("out_trade_no", orderNo);
        request.put("notify_url", config.getNotifyUrl());
        request.put("amount", Map.of("total", amountFen, "currency", "CNY"));
        JsonNode response = post("/v3/pay/transactions/app", json(request));
        String prepayId = response.path("prepay_id").asText();
        if (prepayId.isBlank()) throw new IllegalStateException("微信支付未返回 prepay_id");

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = nonce();
        String clientSignature = PaymentCrypto.sign(
                config.getAppId() + "\n" + timestamp + "\n" + nonce + "\n" + prepayId + "\n",
                privateKey());
        return Map.of(
                "channel", channel(),
                "orderNo", orderNo,
                "appId", config.getAppId(),
                "partnerId", config.getMerchantId(),
                "prepayId", prepayId,
                "packageValue", "Sign=WXPay",
                "nonceStr", nonce,
                "timeStamp", timestamp,
                "sign", clientSignature
        );
    }

    @Override
    public PaymentNotification parseNotification(Map<String, String> headers, Map<String, String> form, String body) {
        requireEnabled();
        JsonNode data = verifiedResource(headers, body);
        boolean paid = "SUCCESS".equals(data.path("trade_state").asText());
        return new PaymentNotification(
                data.path("out_trade_no").asText(),
                data.path("transaction_id").asText(),
                data.path("amount").path("total").asInt(),
                paid);
    }

    @Override
    public RefundNotification parseRefundNotification(Map<String, String> headers, Map<String, String> form, String body) {
        requireEnabled();
        JsonNode data = verifiedResource(headers, body);
        return new RefundNotification(
                data.path("out_refund_no").asText(),
                data.path("refund_id").asText(),
                data.path("amount").path("refund").asInt(),
                "SUCCESS".equals(data.path("refund_status").asText()));
    }

    private JsonNode verifiedResource(Map<String, String> headers, String body) {
        String timestamp = header(headers, "wechatpay-timestamp");
        String nonce = header(headers, "wechatpay-nonce");
        String signature = header(headers, "wechatpay-signature");
        String serial = header(headers, "wechatpay-serial");
        if (!properties.getWechat().getPlatformPublicKeyId().equals(serial)
                || blank(timestamp) || blank(nonce) || blank(signature)
                || !PaymentCrypto.verify(timestamp + "\n" + nonce + "\n" + body + "\n", signature,
                platformPublicKey())) {
            throw new IllegalArgumentException("微信支付回调签名无效");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.path("resource");
            byte[] decrypted = decrypt(
                    resource.path("ciphertext").asText(),
                    resource.path("nonce").asText(),
                    resource.path("associated_data").asText());
            return objectMapper.readTree(decrypted);
        } catch (Exception ex) {
            throw new IllegalArgumentException("微信支付回调内容无法解析", ex);
        }
    }

    @Override
    public RefundResult refund(String orderNo, String refundNo, int amountFen, String reason) {
        requireEnabled();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("out_trade_no", orderNo);
        request.put("out_refund_no", refundNo);
        request.put("reason", reason);
        request.put("notify_url", properties.getWechat().getNotifyUrl());
        request.put("amount", Map.of("refund", amountFen, "total", amountFen, "currency", "CNY"));
        JsonNode response = post("/v3/refund/domestic/refunds", json(request));
        String status = response.path("status").asText();
        return new RefundResult(
                "SUCCESS".equals(status) || "PROCESSING".equals(status),
                response.path("refund_id").asText(refundNo));
    }

    private JsonNode post(String path, String body) {
        PaymentProperties.Wechat config = properties.getWechat();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = nonce();
        String signature = PaymentCrypto.sign(
                "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n", privateKey());
        String authorization = "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + config.getMerchantId() + "\","
                + "nonce_str=\"" + nonce + "\","
                + "timestamp=\"" + timestamp + "\","
                + "serial_no=\"" + config.getMerchantSerialNumber() + "\","
                + "signature=\"" + signature + "\"";
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(config.getApiBase() + path))
                            .header("Authorization", authorization)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "health-reset-plan/1.0")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("微信支付接口返回 " + response.statusCode());
            }
            verifyResponse(response);
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("微信支付请求失败", ex);
        }
    }

    private byte[] decrypt(String ciphertext, String nonce, String associatedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(properties.getWechat().getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        if (!blank(associatedData)) cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(Base64.getDecoder().decode(ciphertext));
    }

    private PrivateKey privateKey() {
        return PaymentCrypto.privateKey(properties.getWechat().getPrivateKeyPath());
    }

    private PublicKey platformPublicKey() {
        return PaymentCrypto.publicKey(properties.getWechat().getPlatformPublicKeyPath());
    }

    private void verifyResponse(HttpResponse<String> response) {
        String timestamp = response.headers().firstValue("Wechatpay-Timestamp").orElse("");
        String nonce = response.headers().firstValue("Wechatpay-Nonce").orElse("");
        String signature = response.headers().firstValue("Wechatpay-Signature").orElse("");
        String serial = response.headers().firstValue("Wechatpay-Serial").orElse("");
        if (!properties.getWechat().getPlatformPublicKeyId().equals(serial)
                || blank(timestamp) || blank(nonce) || blank(signature)
                || !PaymentCrypto.verify(timestamp + "\n" + nonce + "\n" + response.body() + "\n",
                signature, platformPublicKey())) {
            throw new IllegalStateException("微信支付响应签名无效");
        }
    }

    private String header(Map<String, String> headers, String name) {
        if (headers == null) return "";
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("支付参数序列化失败", ex); }
    }

    private String nonce() { return UUID.randomUUID().toString().replace("-", ""); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("微信支付渠道未开通或配置不完整");
    }
}
