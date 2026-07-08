package io.healthresetplan.modules.membership.gateway.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.membership.gateway.PaymentGateway;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AlipayGateway implements PaymentGateway {

    private final AlipayProperties properties;
    private volatile AlipayClient client;

    public AlipayGateway(AlipayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "alipay";
    }

    @Override
    public PrepayResult prepay(PaymentGateway.PrepayRequest request) {
        ensureReady();

        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(request.orderNo());
        model.setSubject(limit(request.subject(), 256));
        model.setTotalAmount(yuan(request.amountFen()));
        model.setProductCode("QUICK_MSECURITY_PAY");

        AlipayTradeAppPayRequest alipayRequest = new AlipayTradeAppPayRequest();
        alipayRequest.setNotifyUrl(properties.getNotifyUrl());
        alipayRequest.setBizModel(model);

        try {
            AlipayTradeAppPayResponse response = client().sdkExecute(alipayRequest);
            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new BusinessException(50031, "支付宝预支付失败");
            }
            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("provider", "alipay");
            credential.put("mode", "app");
            credential.put("orderString", response.getBody());
            return new PrepayResult(credential);
        } catch (AlipayApiException e) {
            throw new BusinessException(50031, "支付宝预支付失败");
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> headers, String body) {
        try {
            return AlipaySignature.rsaCheckV1(
                    parseForm(body),
                    properties.getAlipayPublicKey(),
                    properties.getCharset(),
                    properties.getSignType()
            );
        } catch (AlipayApiException e) {
            return false;
        }
    }

    @Override
    public CallbackResult parseCallback(Map<String, String> headers, String body) {
        Map<String, String> form = parseForm(body);
        String tradeStatus = form.getOrDefault("trade_status", "");
        boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
        return new CallbackResult(
                form.getOrDefault("out_trade_no", ""),
                form.getOrDefault("trade_no", ""),
                paid,
                amountFen(form.get("total_amount"))
        );
    }

    private AlipayClient client() {
        AlipayClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new DefaultAlipayClient(
                            properties.getGatewayUrl(),
                            properties.getAppId(),
                            properties.getPrivateKey(),
                            "json",
                            properties.getCharset(),
                            properties.getAlipayPublicKey(),
                            properties.getSignType()
                    );
                    client = local;
                }
            }
        }
        return local;
    }

    private void ensureReady() {
        if (!properties.isReady()) {
            throw new BusinessException(50030, "支付宝支付配置不完整");
        }
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String key = decode(pair.substring(0, index));
            String value = decode(pair.substring(index + 1));
            params.put(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String yuan(int amountFen) {
        return BigDecimal.valueOf(amountFen, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static Integer amountFen(String totalAmount) {
        if (totalAmount == null || totalAmount.isBlank()) return null;
        return new BigDecimal(totalAmount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static String limit(String value, int maxLength) {
        String text = value == null || value.isBlank() ? "HealthResetPlan会员" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
