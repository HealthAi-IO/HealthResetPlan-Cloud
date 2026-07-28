package io.healthresetplan.modules.captcha.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CaptchaVerifyRequest(
        @NotBlank(message = "captchaId is required") String captchaId,
        @NotBlank(message = "scene is required") String scene,
        @NotBlank(message = "principal is required") String principal,
        double finalX,
        @NotNull @Size(min = 12, max = 300) List<@Valid CaptchaTrajectoryPoint> trajectory) {
}
