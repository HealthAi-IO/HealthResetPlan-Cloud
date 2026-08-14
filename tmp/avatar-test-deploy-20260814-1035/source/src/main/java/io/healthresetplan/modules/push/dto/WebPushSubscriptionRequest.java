package io.healthresetplan.modules.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebPushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        @NotBlank @Size(max = 512) String p256dh,
        @NotBlank @Size(max = 256) String auth,
        @NotBlank @Size(max = 64) String timezone) {
}
