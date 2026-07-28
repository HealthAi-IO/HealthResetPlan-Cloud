package io.healthresetplan.modules.captcha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.modules.captcha.dto.CaptchaCreateRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaCreateResponse;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class CaptchaService {

    public static final String LOGIN_SCENE = "login";
    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(90);
    private static final Duration TICKET_TTL = Duration.ofSeconds(120);
    private static final String CAPTCHA_PREFIX = "captcha:challenge:";
    private static final String TICKET_PREFIX = "captcha:ticket:";
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value then redis.call('DEL', KEYS[1]); end; return value;",
            String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CaptchaImageGenerator imageGenerator;
    private final CaptchaTrajectoryValidator trajectoryValidator;

    public CaptchaService(StringRedisTemplate redis,
                          ObjectMapper objectMapper,
                          CaptchaImageGenerator imageGenerator,
                          CaptchaTrajectoryValidator trajectoryValidator) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.imageGenerator = imageGenerator;
        this.trajectoryValidator = trajectoryValidator;
    }

    public CaptchaCreateResponse create(CaptchaCreateRequest request) {
        String principal = normalizeAndValidate(request.scene(), request.principal());
        CaptchaImageGenerator.GeneratedCaptcha generated = imageGenerator.generate();
        String captchaId = randomToken();
        CaptchaState state = new CaptchaState(
                LOGIN_SCENE,
                HashUtils.sha256Hex(principal),
                generated.targetX(),
                System.currentTimeMillis());
        redis.opsForValue().set(CAPTCHA_PREFIX + captchaId, write(state), CAPTCHA_TTL);
        return new CaptchaCreateResponse(
                captchaId,
                generated.backgroundImageBase64(),
                generated.pieceImageBase64(),
                CaptchaImageGenerator.WIDTH,
                CaptchaImageGenerator.HEIGHT,
                CaptchaImageGenerator.PIECE_SIZE,
                CAPTCHA_TTL.toSeconds());
    }

    public CaptchaVerifyResponse verify(CaptchaVerifyRequest request) {
        String principal = normalizeAndValidate(request.scene(), request.principal());
        String raw = consume(CAPTCHA_PREFIX + request.captchaId());
        if (raw == null) {
            throw new BusinessException(40021, "验证码已过期或已使用");
        }

        CaptchaState state = read(raw, CaptchaState.class);
        if (!state.scene().equals(request.scene())
                || !state.principalHash().equals(HashUtils.sha256Hex(principal))) {
            throw new BusinessException(40022, "验证码场景或账号不匹配");
        }

        int maxX = CaptchaImageGenerator.WIDTH - CaptchaImageGenerator.PIECE_SIZE;
        if (!trajectoryValidator.isValid(
                request.trajectory(), request.finalX(), state.targetX(), maxX)) {
            throw new BusinessException(40023, "滑动验证失败，请重试");
        }

        String ticket = randomToken();
        TicketState ticketState = new TicketState(state.scene(), state.principalHash());
        redis.opsForValue().set(TICKET_PREFIX + ticket, write(ticketState), TICKET_TTL);
        return new CaptchaVerifyResponse(ticket, TICKET_TTL.toSeconds());
    }

    public void consumeLoginTicket(String ticket, String phone) {
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException(40024, "请先完成滑动验证");
        }
        String raw = consume(TICKET_PREFIX + ticket);
        if (raw == null) {
            throw new BusinessException(40024, "滑动验证票据已过期或已使用");
        }
        TicketState state = read(raw, TicketState.class);
        if (!LOGIN_SCENE.equals(state.scene())
                || !state.principalHash().equals(HashUtils.sha256Hex(normalizePhone(phone)))) {
            throw new BusinessException(40025, "滑动验证票据与登录账号不匹配");
        }
    }

    private String normalizeAndValidate(String scene, String principal) {
        if (!LOGIN_SCENE.equals(scene)) {
            throw new BusinessException(40020, "不支持的验证码场景");
        }
        String phone = normalizePhone(principal);
        if (!phone.matches("^1\\d{10}$")) {
            throw new BusinessException(40003, "phone format is invalid");
        }
        return phone;
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String consume(String key) {
        return redis.execute(GET_AND_DELETE, List.of(key));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("验证码状态序列化失败", e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(40021, "验证码状态无效");
        }
    }

    private record CaptchaState(String scene, String principalHash, int targetX, long createdAt) {
    }

    private record TicketState(String scene, String principalHash) {
    }
}
