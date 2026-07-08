package io.healthresetplan.modules.membership.gateway.impl;

import com.wechat.pay.java.core.AbstractRSAConfig;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.AutoCertificateNotificationConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.app.model.Amount;
import com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.membership.gateway.PaymentGateway;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WechatPayGateway implements PaymentGateway {

    private final WechatPayProperties properties;
    private volatile AbstractRSAConfig config;
    private volatile NotificationConfig notificationConfig;
    private volatile AppServiceExtension appService;
    private volatile NotificationParser notificationParser;

    public WechatPayGateway(WechatPayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "wechat";
    }

    @Override
    public PrepayResult prepay(PaymentGateway.PrepayRequest request) {
        ensureReady();

        Amount amount = new Amount();
        amount.setTotal(request.amountFen());
        amount.setCurrency("CNY");

        com.wechat.pay.java.service.payments.app.model.PrepayRequest prepay =
                new com.wechat.pay.java.service.payments.app.model.PrepayRequest();
        prepay.setAppid(properties.getAppId());
        prepay.setMchid(properties.getMchId());
        prepay.setDescription(limit(request.subject(), 127));
        prepay.setOutTradeNo(request.orderNo());
        prepay.setNotifyUrl(properties.getNotifyUrl());
        prepay.setAmount(amount);

        PrepayWithRequestPaymentResponse response = appService().prepayWithRequestPayment(prepay);
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("provider", "wechat");
        credential.put("mode", "app");
        credential.put("appid", response.getAppid());
        credential.put("partnerId", response.getPartnerId());
        credential.put("prepayId", response.getPrepayId());
        credential.put("package", response.getPackageVal());
        credential.put("nonceStr", response.getNonceStr());
        credential.put("timeStamp", response.getTimestamp());
        credential.put("sign", response.getSign());
        return new PrepayResult(credential);
    }

    @Override
    public boolean verifyCallback(Map<String, String> headers, String body) {
        try {
            parseTransaction(headers, body);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public CallbackResult parseCallback(Map<String, String> headers, String body) {
        Transaction transaction = parseTransaction(headers, body);
        boolean paid = Transaction.TradeStateEnum.SUCCESS.equals(transaction.getTradeState());
        return new CallbackResult(
                transaction.getOutTradeNo(),
                transaction.getTransactionId(),
                paid,
                transaction.getAmount() == null ? null : transaction.getAmount().getTotal()
        );
    }

    private Transaction parseTransaction(Map<String, String> headers, String body) {
        ensureReady();
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(header(headers, "Wechatpay-Serial"))
                .timestamp(header(headers, "Wechatpay-Timestamp"))
                .nonce(header(headers, "Wechatpay-Nonce"))
                .signature(header(headers, "Wechatpay-Signature"))
                .signType(header(headers, "Wechatpay-Signature-Type"))
                .body(body)
                .build();
        return notificationParser().parse(requestParam, Transaction.class);
    }

    private AppServiceExtension appService() {
        AppServiceExtension local = appService;
        if (local == null) {
            synchronized (this) {
                local = appService;
                if (local == null) {
                    local = new AppServiceExtension.Builder().config(config()).build();
                    appService = local;
                }
            }
        }
        return local;
    }

    private NotificationParser notificationParser() {
        NotificationParser local = notificationParser;
        if (local == null) {
            synchronized (this) {
                local = notificationParser;
                if (local == null) {
                    local = new NotificationParser(notificationConfig());
                    notificationParser = local;
                }
            }
        }
        return local;
    }

    private NotificationConfig notificationConfig() {
        NotificationConfig local = notificationConfig;
        if (local == null) {
            synchronized (this) {
                local = notificationConfig;
                if (local == null) {
                    local = properties.hasPublicKeyConfig()
                            ? new RSAPublicKeyNotificationConfig.Builder()
                                    .publicKeyFromPath(properties.getPublicKeyPath())
                                    .publicKeyId(properties.getPublicKeyId())
                                    .apiV3Key(properties.getApiV3Key())
                                    .build()
                            : new AutoCertificateNotificationConfig.Builder()
                                    .merchantId(properties.getMchId())
                                    .privateKeyFromPath(properties.getPrivateKeyPath())
                                    .merchantSerialNumber(properties.getMerchantSerialNumber())
                                    .apiV3Key(properties.getApiV3Key())
                                    .build();
                    notificationConfig = local;
                }
            }
        }
        return local;
    }

    private AbstractRSAConfig config() {
        AbstractRSAConfig local = config;
        if (local == null) {
            synchronized (this) {
                local = config;
                if (local == null) {
                    local = properties.hasPublicKeyConfig()
                            ? new RSAPublicKeyConfig.Builder()
                                    .merchantId(properties.getMchId())
                                    .privateKeyFromPath(properties.getPrivateKeyPath())
                                    .merchantSerialNumber(properties.getMerchantSerialNumber())
                                    .publicKeyFromPath(properties.getPublicKeyPath())
                                    .publicKeyId(properties.getPublicKeyId())
                                    .apiV3Key(properties.getApiV3Key())
                                    .build()
                            : new RSAAutoCertificateConfig.Builder()
                                    .merchantId(properties.getMchId())
                                    .privateKeyFromPath(properties.getPrivateKeyPath())
                                    .merchantSerialNumber(properties.getMerchantSerialNumber())
                                    .apiV3Key(properties.getApiV3Key())
                                    .build();
                    config = local;
                }
            }
        }
        return local;
    }

    private void ensureReady() {
        if (!properties.isReady()) {
            throw new BusinessException(50021, "微信支付配置不完整");
        }
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static String limit(String value, int maxLength) {
        String text = value == null || value.isBlank() ? "HealthResetPlan会员" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
