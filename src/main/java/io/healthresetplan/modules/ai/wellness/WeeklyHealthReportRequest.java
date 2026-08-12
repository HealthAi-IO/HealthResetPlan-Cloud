package io.healthresetplan.modules.ai.wellness;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WeeklyHealthReportRequest(
        String provider,
        @NotBlank String startDate,
        @NotBlank String endDate,
        @Min(3) @Max(7) int recordedDays,
        @NotNull Map<String, Object> stats
) {
}
