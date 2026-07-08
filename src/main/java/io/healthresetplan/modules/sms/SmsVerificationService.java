package io.healthresetplan.modules.sms;

import io.healthresetplan.common.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class SmsVerificationService {

    public static final String SCENE_AUTH = "auth";
    public static final String SCENE_PASSWORD_RESET = "password-reset";

    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeCacheService codeCacheService;
    private final SmsRateLimiter rateLimiter;
    private final SmsSender smsSender;
    private final SmsProperties properties;

    public SmsVerificationService(SmsCodeCacheService codeCacheService,
                                  SmsRateLimiter rateLimiter,
                                  SmsSender smsSender,
                                  SmsProperties properties) {
        this.codeCacheService = codeCacheService;
        this.rateLimiter = rateLimiter;
        this.smsSender = smsSender;
        this.properties = properties;
    }

    public SendCodeResult sendPhoneCode(String scene, String phone) {
        String normalizedPhone = normalizePhone(phone);
        String phoneHash = HashUtils.sha256Hex(normalizedPhone);
        rateLimiter.checkPhoneLimit(phoneHash);

        String code = generateCode();
        smsSender.sendVerificationCode(normalizedPhone, code);
        codeCacheService.saveCode(scene, phoneHash, code,
                Duration.ofSeconds(properties.getCodeTtlSeconds()));

        if (properties.isDebugCodeEnabled()) {
            log.info("短信验证码调试码 scene={} phoneTail={} code={}", scene, phoneTail(normalizedPhone), code);
        }
        return new SendCodeResult(
                properties.isDebugCodeEnabled() ? code : "",
                properties.getCodeTtlSeconds()
        );
    }

    public void verifyPhoneCode(String scene, String phone, String code) {
        codeCacheService.verifyAndConsume(scene, HashUtils.sha256Hex(normalizePhone(phone)), code);
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static String phoneTail(String phone) {
        String digits = normalizePhone(phone);
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : "";
    }

    public record SendCodeResult(String debugCode, long expiresIn) {}
}
