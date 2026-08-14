package io.healthresetplan.modules.ai.wellness;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record MenuSwapRequest(
        String provider,
        @Min(1) @Max(7) int dayIndex,
        @NotBlank String mealType,
        @NotNull Map<String, Object> currentMeal,
        List<String> allergies,
        List<String> dislikedFoods,
        double targetCalories
) {
}
