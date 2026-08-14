package io.healthresetplan.modules.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.modules.admin.auth.AdminLoginThrottleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginThrottleServiceTests {

    @Test
    void passwordFailuresAreLimitedByAccountInsteadOfSpoofableIp() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        when(stateStore.increment(
                anyString(), org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(2L);
        PasswordLoginThrottleService service = new PasswordLoginThrottleService(stateStore);

        service.recordFailure("13800138000", "1.1.1.1");
        service.clear("13800138000", "2.2.2.2");

        verify(stateStore).delete("hrp:auth:password-fail:"
                + io.healthresetplan.common.util.HashUtils.sha256Hex("13800138000"));
    }

    @Test
    void adminLoginIsBlockedAfterFiveFailures() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        when(stateStore.get(anyString())).thenReturn("5");
        AdminLoginThrottleService service = new AdminLoginThrottleService(stateStore);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.check(" Admin ")
        );

        assertEquals(42901, exception.getCode());
    }
}
