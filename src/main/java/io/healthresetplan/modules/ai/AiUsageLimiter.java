package io.healthresetplan.modules.ai;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiUsageLimiter {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    public enum Type {
        CHAT("chat", 30),
        PLAN("plan", 3),
        REPORT("report", 5);

        private final String key;
        private final int limit;

        Type(String key, int limit) {
            this.key = key;
            this.limit = limit;
        }
    }

    private final StringRedisTemplate redis;

    public AiUsageLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void consume(String userId, Type type) {
        String key = key(userId, type);
        Long used = redis.opsForValue().increment(key);
        if (used != null && used == 1) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (used != null && used > type.limit) {
            redis.opsForValue().decrement(key);
            throw new BusinessException(42901, "今日" + label(type) + "次数已达上限（" + type.limit + " 次）");
        }
    }

    public long used(String userId, Type type) {
        String value = redis.opsForValue().get(key(userId, type));
        return value == null ? 0 : Long.parseLong(value);
    }

    public int limit(Type type) { return type.limit; }

    public Map<String, Object> usage(String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Type type : Type.values()) {
            long used = used(userId, type);
            result.put(type.key, Map.of("used", used, "limit", type.limit,
                    "remaining", Math.max(0, type.limit - used)));
        }
        return result;
    }

    private String key(String userId, Type type) {
        return "hrp:ai:usage:" + LocalDate.now(CHINA_ZONE) + ":" + type.key + ":" + userId;
    }

    private String label(Type type) {
        return switch (type) {
            case CHAT -> "AI 对话";
            case PLAN -> "计划生成";
            case REPORT -> "报告识别";
        };
    }
}
