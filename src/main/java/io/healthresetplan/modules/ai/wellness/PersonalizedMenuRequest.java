package io.healthresetplan.modules.ai.wellness;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PersonalizedMenuRequest(
        String provider,
        @Min(14) @Max(120) int age,
        @NotBlank String gender,
        double heightCm,
        double weightKg,
        String medicalHistory,
        String medications,
        @NotBlank String goal,
        String goalDetail,
        String dietPreference,
        List<String> allergies,
        List<String> dislikedFoods,
        Double budgetPerDay,
        Integer cookingMinutes,
        List<String> equipment,
        double targetCalories,
        double proteinG,
        double carbsG,
        double fatG,
        @NotBlank String startDate
) {
}
