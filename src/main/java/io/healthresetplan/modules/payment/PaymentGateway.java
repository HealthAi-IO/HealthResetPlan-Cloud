package io.healthresetplan.modules.payment;

import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentGateway {
    String channel();
    boolean enabled();
    Map<String, Object> createPayment(String orderNo, String subject, int amountFen, LocalDateTime expiresAt);
    PaymentNotification parseNotification(Map<String, String> headers, Map<String, String> form, String body);
    default RefundNotification parseRefundNotification(Map<String, String> headers, Map<String, String> form, String body) {
        throw new IllegalArgumentException("该渠道不支持退款回调");
    }
    RefundResult refund(String orderNo, String refundNo, int amountFen, String reason);

    record PaymentNotification(String orderNo, String channelOrderNo, int amountFen, boolean paid) {}
    record RefundNotification(String refundNo, String channelRefundNo, int amountFen, boolean succeeded) {}
    record RefundResult(boolean accepted, String channelRefundNo) {}
}
