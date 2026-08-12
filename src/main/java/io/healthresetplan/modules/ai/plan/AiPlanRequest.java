package io.healthresetplan.modules.ai.plan;

/**
 * AI 健康方案生成请求。
 *
 * @param provider     大模型提供方：deepseek / glm / doubao / qwen（可选）
 * @param age          年龄
 * @param gender       性别：male / female
 * @param heightCm     身高（cm）
 * @param weightKg     体重（kg）
 * @param bmi          BMI（客户端计算）
 * @param medicalHistory 健康史（脱敏，不含姓名/电话等）
 * @param recentBp     最近血压，如 "120/80"
 * @param recentGlucose 最近空腹血糖（mmol/L）
 * @param recentTc     最近总胆固醇（mmol/L）
 * @param recentLdl    最近 LDL（mmol/L）
 * @param goal         健康目标：lose_weight / lower_bp / lower_glucose / lower_lipid / general
 * @param goalDetail   用户对目标状态的自由补充
 * @param targetDate   用户希望达到目标状态的日期，格式 yyyy-MM-dd
 * @param dietPref     饮食偏好：normal / vegetarian / light
 * @param exerciseBase 运动基础：none / light / moderate
 */
public record AiPlanRequest(
        String provider,
        int age,
        String gender,
        double heightCm,
        double weightKg,
        double bmi,
        String medicalHistory,
        String recentBp,
        Double recentGlucose,
        Double recentTc,
        Double recentLdl,
        String goal,
        String goalDetail,
        String targetDate,
        String dietPref,
        String exerciseBase
) {
}
