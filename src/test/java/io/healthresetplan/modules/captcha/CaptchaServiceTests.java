package io.healthresetplan.modules.captcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.captcha.dto.CaptchaCreateRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaTrajectoryPoint;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTests {

    @Test
    void challengeAndTicketAreBoundAndConsumedOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        CaptchaImageGenerator generator = mock(CaptchaImageGenerator.class);
        Map<String, String> cache = new HashMap<>();

        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.<String>anyList()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    return cache.remove(keys.get(0));
                });
        when(generator.generate()).thenReturn(
                new CaptchaImageGenerator.GeneratedCaptcha(123, "background", "piece"));

        CaptchaService service = new CaptchaService(
                redis,
                new ObjectMapper(),
                generator,
                new CaptchaTrajectoryValidator());

        String phone = "13800138000";
        String captchaId = service.create(new CaptchaCreateRequest("login", phone)).captchaId();
        verify(values).set(
                startsWith("captcha:challenge:"),
                anyString(),
                eq(Duration.ofSeconds(90)));

        CaptchaVerifyResponse result = service.verify(new CaptchaVerifyRequest(
                captchaId,
                "login",
                phone,
                124,
                humanTrajectory()));
        assertFalse(result.ticket().isBlank());
        verify(values).set(
                startsWith("captcha:ticket:"),
                anyString(),
                eq(Duration.ofSeconds(120)));

        service.consumeLoginTicket(result.ticket(), phone);
        assertThrows(
                BusinessException.class,
                () -> service.consumeLoginTicket(result.ticket(), phone));
    }

    private List<CaptchaTrajectoryPoint> humanTrajectory() {
        long[] times = {0, 31, 67, 108, 154, 205, 261, 322, 388, 459, 535, 616, 702};
        double[] xs = {0, 2, 7, 16, 29, 45, 64, 83, 99, 111, 119, 123, 124};
        double[] ys = {100, 100.3, 100.8, 101.4, 101.1, 100.5, 99.8, 99.4, 99.9, 100.7, 101.2, 100.6, 100.1};
        List<CaptchaTrajectoryPoint> points = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            points.add(new CaptchaTrajectoryPoint(xs[i], ys[i], times[i]));
        }
        return points;
    }
}
