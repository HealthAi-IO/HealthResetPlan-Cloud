package io.healthresetplan.modules.captcha.dto;

public record CaptchaVerifyResponse(String ticket, long expiresIn) {
}
