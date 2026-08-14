package io.healthresetplan.modules.sms;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SmsRateLimiter {

    private static final String INTERVAL_PREFIX = "hrp:sms:limit:interval:";
    private static final String HOURLY_PREFIX = "hrp:sms:limit:hour:";
    private static final String DAILY_PREFIX = "hrp:sms:limit:day:";

    private final ExpiringStateStore stateStore;
    private final SmsProperties properties;

    public SmsRateLimiter(ExpiringStateStore stateStore, SmsProperties properties) {
        this.stateStore = stateStore;
        this.properties = properties;
    }

    public void checkPhoneLimit(String phoneHash) {
        checkResendInterval(phoneHash);
        checkWindow(HOURLY_PREFIX + phoneHash, properties.getMaxPerPhonePerHour(),
                Duration.ofHours(1), "验证码发送过于频繁，请稍后再试");
        checkWindow(DAILY_PREFIX + phoneHash, properties.getMaxPerPhonePerDay(),
                Duration.ofDays(1), "今日验证码发送次数已达上限，请明天再试");
    }

    private void checkResendInterval(String phoneHash) {
        Duration interval = Duration.ofSeconds(properties.getResendIntervalSeconds());
        boolean firstSend = stateStore.putIfAbsent(INTERVAL_PREFIX + phoneHash, "1", interval);
        if (!firstSend) {
            throw new BusinessException(42901,
                    properties.getResendIntervalSeconds() + " 秒内只能发送一次验证码");
        }
    }

    private void checkWindow(String key, int maxCount, Duration ttl, String message) {
        long count = stateStore.increment(key, 1, ttl);
        if (count > maxCount) {
            throw new BusinessException(42901, message);
        }
    }
}
