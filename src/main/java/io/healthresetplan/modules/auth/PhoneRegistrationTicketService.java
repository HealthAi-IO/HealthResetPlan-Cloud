package io.healthresetplan.modules.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.common.util.HashUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class PhoneRegistrationTicketService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "hrp:auth:register-ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExpiringStateStore stateStore;

    public PhoneRegistrationTicketService(ExpiringStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public String issue(String phone) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stateStore.put(key(ticket), HashUtils.sha256Hex(phone), TTL);
        return ticket;
    }

    public void verify(String ticket, String phone) {
        String expectedPhoneHash = stateStore.get(key(ticket));
        if (!HashUtils.sha256Hex(phone).equals(expectedPhoneHash)) {
            throw new BusinessException(40001, "手机号验证已过期，请重新获取验证码");
        }
    }

    public void consume(String ticket) {
        stateStore.delete(key(ticket));
    }

    public long expiresInSeconds() {
        return TTL.toSeconds();
    }

    private String key(String ticket) {
        return PREFIX + ticket;
    }
}
