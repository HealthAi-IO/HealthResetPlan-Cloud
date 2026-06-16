package io.healthresetplan.modules.sms;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SmsCodeCacheService {

    private static final String CODE_PREFIX = "hrp:sms:code:";
    private static final String USED_PREFIX = "hrp:sms:used:";

    private final StringRedisTemplate redisTemplate;

    public SmsCodeCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveCode(String scene, String identifierHash, String code, Duration ttl) {
        redisTemplate.opsForValue().set(codeKey(scene, identifierHash), code, ttl);
        redisTemplate.delete(usedKey(scene, identifierHash, code));
    }

    public void verifyAndConsume(String scene, String identifierHash, String code) {
        String key = codeKey(scene, identifierHash);
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            throw new BusinessException(40001, "验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code)) {
            throw new BusinessException(40002, "验证码错误");
        }

        Boolean used = redisTemplate.opsForValue().setIfAbsent(
                usedKey(scene, identifierHash, code),
                "1",
                Duration.ofMinutes(10)
        );
        if (Boolean.FALSE.equals(used)) {
            throw new BusinessException(40002, "验证码已使用，请重新获取");
        }
        redisTemplate.delete(key);
    }

    private String codeKey(String scene, String identifierHash) {
        return CODE_PREFIX + scene + ":" + identifierHash;
    }

    private String usedKey(String scene, String identifierHash, String code) {
        return USED_PREFIX + scene + ":" + identifierHash + ":" + code;
    }
}
