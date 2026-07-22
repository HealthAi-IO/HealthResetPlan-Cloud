package io.healthresetplan.modules.sms;

import io.healthresetplan.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsCodeCacheServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SmsCodeCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new SmsCodeCacheService(redisTemplate);
    }

    @Test
    void fifthIncorrectAttemptInvalidatesCode() {
        when(valueOperations.get("hrp:sms:code:login:user-hash")).thenReturn("123456");
        when(valueOperations.increment("hrp:sms:failure:login:user-hash")).thenReturn(5L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.verifyAndConsume("login", "user-hash", "000000")
        );

        assertEquals(42901, error.getCode());
        verify(redisTemplate).delete("hrp:sms:code:login:user-hash");
    }
}
