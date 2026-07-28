package io.healthresetplan.modules.captcha.dto;

public record CaptchaCreateResponse(
        String captchaId,
        String backgroundImageBase64,
        String pieceImageBase64,
        int imageWidth,
        int imageHeight,
        int pieceWidth,
        long expiresIn) {
}
