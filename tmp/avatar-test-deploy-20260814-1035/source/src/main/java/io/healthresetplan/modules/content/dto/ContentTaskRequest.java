package io.healthresetplan.modules.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ContentTaskRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String contentType,
        @NotBlank @Size(max = 500) String topic,
        @NotBlank String scheduleType,
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime publishTime,
        @NotBlank String publishMode,
        @Size(max = 64) String preferredProvider,
        boolean imageEnabled,
        boolean enabled
) {
}
