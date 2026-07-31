package io.healthresetplan.modules.ai;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
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
        CHAT("chat", 66),
        PLAN("plan", 3),
        REPORT("report", 5),
        IMAGE("image", 5);

        private final String key;
        private final int limit;

        Type(String key, int limit) {
            this.key = key;
            this.limit = limit;
        }
    }

    private final ExpiringStateStore stateStore;

    public AiUsageLimiter(ExpiringStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void consume(String userId, Type type) {
        String key = key(userId, type);
        long used = stateStore.increment(key, 1, Duration.ofDays(2));
        if (used > type.limit) {
            stateStore.increment(key, -1, Duration.ofDays(2));
            throw new BusinessException(42901, "今日" + label(type) + "次数已达上限（" + type.limit + " 次）");
        }
    }

    public void release(String userId, Type type) {
        String key = key(userId, type);
        long used = stateStore.increment(key, -1, Duration.ofDays(2));
        if (used <= 0) {
            stateStore.delete(key);
        }
    }

    public long used(String userId, Type type) {
        String value = stateStore.get(key(userId, type));
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
            case IMAGE -> "图片分析";
        };
    }
}
