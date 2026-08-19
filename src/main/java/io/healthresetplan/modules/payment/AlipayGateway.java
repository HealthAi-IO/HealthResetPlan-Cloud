package io.healthresetplan.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
public class AlipayGateway implements PaymentGateway {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AlipayGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() { return "alipay"; }

    @Override
    public boolean enabled() {
        PaymentProperties.Alipay config = properties.getAlipay();
        return config.isEnabled() && !blank(config.getAppId())
                && !blank(config.getPrivateKeyPath()) && !blank(config.getAlipayPublicKeyPath());
    }

    @Override
    public Map<String, Object> createPayment(String orderNo, String subject, int amountFen, LocalDateTime expiresAt) {
        requireEnabled();
        PaymentProperties.Alipay config = properties.getAlipay();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.getAppId());
        params.put("method", "alipay.trade.app.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TIME));
        params.put("version", "1.0");
        params.put("notify_url", config.getNotifyUrl());
        params.put("biz_content", json(Map.of(
                "out_trade_no", orderNo,
                "subject", subject,
                "total_amount", String.format("%.2f", amountFen / 100.0),
                "product_code", "QUICK_MSECURITY_PAY",
                "timeout_express", "15m"
        )));
        String content = canonical(params);
        String sign = PaymentCrypto.sign(content, privateKey(config.getPrivateKeyPath()));
        String orderString = encodeParams(params) + "&sign=" + encode(sign);
        return Map.of("channel", channel(), "orderString", orderString, "orderNo", orderNo);
    }

    @Override
    public PaymentNotification parseNotification(Map<String, String> headers, Map<String, String> form, String body) {
        if (!enabled() || form == null || form.isEmpty()) {
            throw new IllegalArgumentException("支付宝回调参数为空或支付渠道未开通");
        }
        PaymentProperties.Alipay config = properties.getAlipay();
        if (!config.getAppId().equals(form.get("app_id"))) {
            throw new IllegalArgumentException("支付宝回调应用不匹配");
        }
        String sign = form.get("sign");
        Map<String, String> signed = new TreeMap<>(form);
        signed.remove("sign");
        signed.remove("sign_type");
        if (blank(sign) || !PaymentCrypto.verify(canonical(signed), sign,
                publicKey(config.getAlipayPublicKeyPath()))) {
            throw new IllegalArgumentException("支付宝回调签名无效");
        }
        String tradeStatus = form.get("trade_status");
        boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
        int amountFen = (int) Math.round(Double.parseDouble(form.getOrDefault("total_amount", "0")) * 100);
        return new PaymentNotification(form.get("out_trade_no"), form.get("trade_no"), amountFen, paid);
    }

    @Override
    public RefundResult refund(String orderNo, String refundNo, int amountFen, String reason) {
        requireEnabled();
        PaymentProperties.Alipay config = properties.getAlipay();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.getAppId());
        params.put("method", "alipay.trade.refund");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TIME));
        params.put("version", "1.0");
        params.put("biz_content", json(Map.of(
                "out_trade_no", orderNo,
                "out_request_no", refundNo,
                "refund_amount", String.format("%.2f", amountFen / 100.0),
                "refund_reason", reason
        )));
        params.put("sign", PaymentCrypto.sign(canonical(params), privateKey(config.getPrivateKeyPath())));
        try {
            String response = httpClient.send(HttpRequest.newBuilder(URI.create(config.getGateway()))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeParams(params)))
                    .build(), HttpResponse.BodyHandlers.ofString()).body();
            JsonNode node = objectMapper.readTree(response).path("alipay_trade_refund_response");
            boolean accepted = "10000".equals(node.path("code").asText());
            return new RefundResult(accepted, node.path("trade_no").asText(refundNo));
        } catch (Exception ex) {
            throw new IllegalStateException("支付宝退款请求失败", ex);
        }
    }

    private void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("支付宝支付渠道未开通或配置不完整");
    }

    private PrivateKey privateKey(String path) { return PaymentCrypto.privateKey(path); }
    private PublicKey publicKey(String path) { return PaymentCrypto.publicKey(path); }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("支付参数序列化失败", ex); }
    }

    private String canonical(Map<String, String> source) {
        return new TreeMap<>(source).entrySet().stream()
                .filter(entry -> !blank(entry.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String encodeParams(Map<String, String> source) {
        return source.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
