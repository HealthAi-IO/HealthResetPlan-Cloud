package io.healthresetplan.modules.sms;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsCodeCacheServiceTests {

    @Mock
    private ExpiringStateStore stateStore;

    private SmsCodeCacheService service;

    @BeforeEach
    void setUp() {
        service = new SmsCodeCacheService(stateStore);
    }

    @Test
    void fifthIncorrectAttemptInvalidatesCode() {
        when(stateStore.get("hrp:sms:code:login:user-hash")).thenReturn("123456");
        when(stateStore.increment(
                "hrp:sms:failure:login:user-hash", 1, java.time.Duration.ofMinutes(10)))
                .thenReturn(5L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.verifyAndConsume("login", "user-hash", "000000")
        );

        assertEquals(42901, error.getCode());
        verify(stateStore).delete("hrp:sms:code:login:user-hash");
    }
}
