package io.healthresetplan.modules.captcha.dto;

import jakarta.validation.constraints.NotBlank;

public record CaptchaCreateRequest(
        @NotBlank(message = "scene is required") String scene,
        @NotBlank(message = "principal is required") String principal) {
}
