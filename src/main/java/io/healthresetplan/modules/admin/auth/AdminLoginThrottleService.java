package io.healthresetplan.modules.admin.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AdminLoginThrottleService {

    private static final String PREFIX = "hrp:admin:login-fail:";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public AdminLoginThrottleService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String username) {
        String value = redis.opsForValue().get(key(username));
        if (value != null && Integer.parseInt(value) >= MAX_FAILURES) {
            throw new BusinessException(42901, "登录失败次数过多，请 15 分钟后再试");
        }
    }

    public void recordFailure(String username) {
        String key = key(username);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
    }

    public void clear(String username) {
        redis.delete(key(username));
    }

    private String key(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        return PREFIX + HashUtils.sha256Hex(normalized);
    }
}
