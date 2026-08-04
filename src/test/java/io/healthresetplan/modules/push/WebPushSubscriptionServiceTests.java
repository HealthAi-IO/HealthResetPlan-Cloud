package io.healthresetplan.modules.push;

import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.config.WebPushProperties;
import io.healthresetplan.modules.push.dto.WebPushSubscriptionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebPushSubscriptionServiceTests {
    private final WebPushSubscriptionService service = new WebPushSubscriptionService(
            mock(JdbcTemplate.class), mock(DataEncryptionService.class), new WebPushProperties());

    @Test
    void rejectsNonPushEndpointToPreventServerSideRequestForgery() {
        String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[65]);
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        var request = new WebPushSubscriptionRequest(
                "https://example.com/internal", publicKey, auth, "Asia/Shanghai");

        assertThrows(BusinessException.class, () -> service.subscribe("user-1", "device-1", request));
    }
}
