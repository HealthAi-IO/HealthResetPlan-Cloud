package io.healthresetplan.modules.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.admin.auth.AdminLoginThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginThrottleServiceTests {

    @Test
    void passwordFailuresAreLimitedByAccountInsteadOfSpoofableIp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(2L);
        PasswordLoginThrottleService service = new PasswordLoginThrottleService(redis);

        service.recordFailure("13800138000", "1.1.1.1");
        service.clear("13800138000", "2.2.2.2");

        verify(redis).delete("hrp:auth:password-fail:"
                + io.healthresetplan.common.util.HashUtils.sha256Hex("13800138000"));
    }

    @Test
    void adminLoginIsBlockedAfterFiveFailures() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("5");
        AdminLoginThrottleService service = new AdminLoginThrottleService(redis);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.check(" Admin ")
        );

        assertEquals(42901, exception.getCode());
    }
}
