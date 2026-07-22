package io.healthresetplan.modules.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class PhoneRegistrationTicketService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "hrp:auth:register-ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public PhoneRegistrationTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(String phone) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(key(ticket), HashUtils.sha256Hex(phone), TTL);
        return ticket;
    }

    public void verify(String ticket, String phone) {
        String expectedPhoneHash = redisTemplate.opsForValue().get(key(ticket));
        if (!HashUtils.sha256Hex(phone).equals(expectedPhoneHash)) {
            throw new BusinessException(40001, "手机号验证已过期，请重新获取验证码");
        }
    }

    public void consume(String ticket) {
        redisTemplate.delete(key(ticket));
    }

    public long expiresInSeconds() {
        return TTL.toSeconds();
    }

    private String key(String ticket) {
        return PREFIX + ticket;
    }
}
