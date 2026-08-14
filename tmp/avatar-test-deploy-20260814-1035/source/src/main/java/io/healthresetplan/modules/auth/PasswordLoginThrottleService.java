package io.healthresetplan.modules.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.common.util.HashUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PasswordLoginThrottleService {

    private static final String PREFIX = "hrp:auth:password-fail:";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ExpiringStateStore stateStore;

    public PasswordLoginThrottleService(ExpiringStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void check(String phone, String ip) {
        String value = stateStore.get(key(phone, ip));
        if (value != null && Integer.parseInt(value) >= MAX_FAILURES) {
            throw new BusinessException(42901, "登录失败次数过多，请 15 分钟后再试");
        }
    }

    public void recordFailure(String phone, String ip) {
        String key = key(phone, ip);
        stateStore.increment(key, 1, WINDOW);
    }

    public void clear(String phone, String ip) {
        stateStore.delete(key(phone, ip));
    }

    private String key(String phone, String ip) {
        return PREFIX + HashUtils.sha256Hex(phone);
    }
}
