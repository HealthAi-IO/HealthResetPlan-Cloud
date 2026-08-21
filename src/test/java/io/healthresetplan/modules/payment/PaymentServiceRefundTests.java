package io.healthresetplan.modules.payment;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceRefundTests {

    @Test
    void alipayRefundCompletesAutomatically() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.channel()).thenReturn("alipay");
        when(gateway.refund(anyString(), anyString(), eq(990), eq("重复购买")))
                .thenReturn(new PaymentGateway.RefundResult(true, "channel-refund-1"));

        Map<String, Object> result = service(gateway).requestRefund("user-1", "order-1", "重复购买");

        assertEquals("completed", result.get("status"));
    }

    @Test
    void wechatRefundWaitsForChannelCallback() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.channel()).thenReturn("wechat");
        when(gateway.refund(anyString(), anyString(), eq(990), eq("误操作购买")))
                .thenReturn(new PaymentGateway.RefundResult(true, "channel-refund-2"));

        Map<String, Object> result = service(gateway).requestRefund("user-1", "order-1", "误操作购买");

        assertEquals("processing", result.get("status"));
    }

    @Test
    void uncertainChannelResultFallsBackToManualHandling() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.channel()).thenReturn("alipay");
        when(gateway.refund(anyString(), anyString(), eq(990), eq("支付方式有疑问")))
                .thenThrow(new IllegalStateException("channel timeout"));

        Map<String, Object> result = service(gateway).requestRefund("user-1", "order-1", "支付方式有疑问");

        assertEquals("needs_manual", result.get("status"));
    }

    private PaymentService service(PaymentGateway gateway) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> order = Map.of(
                "order_no", "order-1",
                "user_id", "user-1",
                "channel", gateway.channel(),
                "status", "paid",
                "amount_fen", 990,
                "credit_amount", 20,
                "remaining_credit", 20,
                "paid_at", LocalDateTime.now().minusMinutes(5));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(order));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(20);
        return new PaymentService(jdbc, new PaymentProperties(), List.of(gateway));
    }
}
