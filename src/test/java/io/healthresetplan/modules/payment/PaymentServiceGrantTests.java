package io.healthresetplan.modules.payment;

import io.healthresetplan.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceGrantTests {

    @Test
    void grantAddsCreditsAndReturnsNewBalance() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "user_id", "user-1", "nickname", "测试用户", "phone_tail", "1234", "status", 1)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(13);

        Map<String, Object> result = service(jdbc).grantCredits(
                "user-1", 10, "客服补偿", 1L, "127.0.0.1", "test");

        assertEquals(10, result.get("amount"));
        assertEquals(13, result.get("balance"));
    }

    @Test
    void grantRejectsInvalidAmount() {
        BusinessException error = assertThrows(BusinessException.class, () -> service(mock(JdbcTemplate.class))
                .grantCredits("user-1", 0, "客服补偿", 1L, "127.0.0.1", "test"));
        assertEquals(40001, error.getCode());
    }

    private PaymentService service(JdbcTemplate jdbc) {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.channel()).thenReturn("test");
        return new PaymentService(jdbc, new PaymentProperties(), List.of(gateway));
    }
}
