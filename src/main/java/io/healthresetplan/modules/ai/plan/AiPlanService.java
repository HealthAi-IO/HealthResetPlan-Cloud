package io.healthresetplan.modules.ai.plan;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);

    private static final String SYSTEM_PROMPT = """
            你是「健康重启计划」专属健康管理顾问 AI，擅长高血压、高血脂、糖尿病前期及体重管理。

            请根据用户健康档案，生成个性化的 7 天健康管理方案。

            【重要】严格按照以下 JSON 格式输出，禁止包含代码块标记（```）或任何额外文字：
            {
              "summary": "本周方案概要，30字以内",
              "keyFocus": "本周核心目标，15字以内",
              "riskAlert": "健康风险提示（若无异常填 null）",
              "targetCalories": 1800,
              "days": [
                {
                  "dayIndex": 1,
                  "weekDay": "周一",
                  "diet": {
                    "breakfast": "早餐具体描述",
                    "lunch": "午餐具体描述",
                    "dinner": "晚餐具体描述",
                    "snack": "加餐建议",
                    "notes": "当日饮食要点"
                  },
                  "exercise": {
                    "type": "运动类型",
                    "durationMinutes": 30,
                    "intensity": "低强度",
                    "description": "具体运动建议"
                  },
                  "reminders": ["提醒1", "提醒2"]
                }
              ]
            }
            只输出纯 JSON，饮食建议具体到食材和份量，不作诊断，指标异常须在 riskAlert 提示就医。
            """;

    private final OneApiService oneApiService;
    private final MembershipService membershipService;

    public AiPlanService(OneApiService oneApiService, MembershipService membershipService) {
        this.oneApiService = oneApiService;
        this.membershipService = membershipService;
    }

    public AiPlanResponse generate(String userId, AiPlanRequest req) {
        if (!membershipService.hasFeature(userId, "cloud_sync")) {
            throw new BusinessException(40301, "AI 方案生成是会员专属功能，请先开通会员");
        }

        List<ChatCompletionMessageParam> messages = List.of(
                OneApiService.systemMsg(SYSTEM_PROMPT),
                OneApiService.userMsg(buildUserMessage(req))
        );

        String rawJson = oneApiService.complete(userId, messages);
        rawJson = cleanJson(rawJson);

        log.info("AI 计划生成成功 userId={}", userId);
        return new AiPlanResponse("oneapi", rawJson, 0, 0);
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private String buildUserMessage(AiPlanRequest req) {
        String gender = "male".equals(req.gender()) ? "男" : "女";
        String goal = switch (req.goal() != null ? req.goal() : "general") {
            case "lose_weight" -> "减重控脂";
            case "lower_bp" -> "血压管理";
            case "lower_glucose" -> "血糖控制";
            case "lower_lipid" -> "血脂调节";
            default -> "综合健康管理";
        };
        String diet = switch (req.dietPref() != null ? req.dietPref() : "normal") {
            case "vegetarian" -> "素食";
            case "light" -> "清淡低盐";
            default -> "均衡饮食";
        };
        String exercise = switch (req.exerciseBase() != null ? req.exerciseBase() : "none") {
            case "light" -> "轻度（偶尔散步）";
            case "moderate" -> "中等（每周3–4次有氧）";
            default -> "几乎无运动习惯";
        };

        StringBuilder sb = new StringBuilder("【用户健康档案】\n");
        sb.append("- 性别：").append(gender).append("，年龄：").append(req.age()).append(" 岁\n");
        sb.append("- 身高：").append(req.heightCm()).append(" cm，体重：").append(req.weightKg())
                .append(" kg，BMI：").append(String.format("%.1f", req.bmi())).append("\n");
        if (req.recentBp() != null && !req.recentBp().isBlank())
            sb.append("- 最近血压：").append(req.recentBp()).append(" mmHg\n");
        if (req.recentGlucose() != null)
            sb.append("- 空腹血糖：").append(req.recentGlucose()).append(" mmol/L\n");
        if (req.recentTc() != null) {
            sb.append("- 总胆固醇：").append(req.recentTc()).append(" mmol/L");
            if (req.recentLdl() != null) sb.append("，LDL：").append(req.recentLdl()).append(" mmol/L");
            sb.append("\n");
        }
        if (req.medicalHistory() != null && !req.medicalHistory().isBlank())
            sb.append("- 健康史：").append(req.medicalHistory()).append("\n");
        sb.append("- 目标：").append(goal)
                .append("，饮食：").append(diet)
                .append("，运动基础：").append(exercise).append("\n");
        sb.append("\n请生成符合以上档案的 7 天健康管理方案（JSON 格式）。");
        return sb.toString();
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start > 0 && end > start) s = s.substring(start + 1, end).trim();
        }
        return s;
    }
}
