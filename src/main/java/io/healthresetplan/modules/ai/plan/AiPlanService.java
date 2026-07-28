package io.healthresetplan.modules.ai.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.MedicalRiskGuard;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CACHE_PREFIX = "hrp:ai:plan:";
    private static final String PLAN_PROMPT_VERSION = "v3-json";

    private static final String SYSTEM_PROMPT = """
            你是健康管理顾问。根据用户档案生成 7 天健康方案，只输出可 JSON.parse 的纯 JSON，不要 Markdown。
            必须完全符合结构：
            {
              "summary": "20字内",
              "keyFocus": "12字内",
              "riskAlert": null,
              "targetCalories": 1800,
              "days": [
                {
                  "dayIndex": 1,
                  "weekDay": "周一",
                  "diet": {
                    "breakfast": "燕麦30g+蛋1个",
                    "lunch": "糙米100g+鸡胸100g",
                    "dinner": "鱼100g+青菜300g",
                    "snack": "无糖酸奶100g",
                    "notes": "低盐控油"
                  },
                  "exercise": {
                    "type": "运动类型",
                    "durationMinutes": 30,
                    "intensity": "低强度",
                    "description": "快走30分钟"
                  },
                  "reminders": ["晨起称重", "晚间记录血压"]
                }
              ]
            }
            约束：
            1. days 正好 7 条，dayIndex 为 1-7，weekDay 从周一到周日。
            2. 每餐 18 字内，notes 10 字内，exercise.description 16 字内，reminders 最多 2 条。
            3. 食材要有份量；不作诊断；指标异常只在 riskAlert 简短提醒就医。
            """;

    private final OneApiService oneApiService;
    private final MembershipService membershipService;
    private final OneApiProperties oneApiProperties;
    private final StringRedisTemplate redisTemplate;
    private final AiUsageLimiter usageLimiter;

    public AiPlanService(OneApiService oneApiService,
                         MembershipService membershipService,
                         OneApiProperties oneApiProperties,
                         StringRedisTemplate redisTemplate,
                         AiUsageLimiter usageLimiter) {
        this.oneApiService = oneApiService;
        this.membershipService = membershipService;
        this.oneApiProperties = oneApiProperties;
        this.redisTemplate = redisTemplate;
        this.usageLimiter = usageLimiter;
    }

    public AiPlanResponse generate(String userId, AiPlanRequest req) {
        validateProfile(req);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(40301, "请先登录手机号账号");
        }
        String safetyReply = MedicalRiskGuard.safetyReply(buildUserMessage(req));
        if (safetyReply != null) {
            throw new BusinessException(42201, safetyReply);
        }

        List<ChatCompletionMessageParam> messages = List.of(
                OneApiService.systemMsg(SYSTEM_PROMPT),
                OneApiService.userMsg(buildUserMessage(req))
        );

        String preferredProvider = req.provider() != null && !req.provider().isBlank()
                ? req.provider()
                : null;
        usageLimiter.consume(userId, AiUsageLimiter.Type.PLAN);
        try {
            long maxCompletionTokens = Math.max(1200L, oneApiProperties.getPlanMaxCompletionTokens());
            OneApiService.AiCompletion completion = oneApiService.completeJsonWithProvider(
                    userId,
                    messages,
                    preferredProvider,
                    maxCompletionTokens);
            String rawJson = extractJson(completion.content());
            if (!isUsablePlan(rawJson)) {
                log.warn("AI plan JSON invalid userId={} provider={}", userId, completion.provider());
                throw new BusinessException(50301, "AI 方案格式异常，请重试或切换模型");
            }
            log.info("AI 计划生成成功 provider={}", completion.provider());
            return new AiPlanResponse(completion.provider(), rawJson, 0, 0);
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.PLAN);
            throw e;
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private void validateProfile(AiPlanRequest req) {
        if (req.age() < 14 || req.age() > 120
                || !List.of("male", "female").contains(req.gender())
                || req.heightCm() < 80 || req.heightCm() > 250
                || req.weightKg() < 20 || req.weightKg() > 400
                || req.goal() == null || req.goal().isBlank()) {
            throw new BusinessException(40001, "请先完善性别、出生年份、身高、体重和健康目标，再生成个性化 AI 计划");
        }
    }

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

        StringBuilder sb = new StringBuilder("档案：");
        sb.append(gender).append("，").append(req.age()).append("岁，");
        sb.append(req.heightCm()).append("cm/").append(req.weightKg())
                .append("kg，BMI=").append(String.format("%.1f", req.bmi()));
        if (req.recentBp() != null && !req.recentBp().isBlank())
            sb.append("，血压=").append(req.recentBp());
        if (req.recentGlucose() != null)
            sb.append("，空腹血糖=").append(req.recentGlucose());
        if (req.recentTc() != null) {
            sb.append("，TC=").append(req.recentTc());
            if (req.recentLdl() != null) sb.append("，LDL=").append(req.recentLdl());
        }
        if (req.medicalHistory() != null && !req.medicalHistory().isBlank())
            sb.append("，健康史=").append(req.medicalHistory());
        sb.append("。目标=").append(goal)
                .append("；饮食=").append(diet)
                .append("；运动基础=").append(exercise)
                .append("。生成短 JSON。");
        return sb.toString();
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start > 0 && end > start) s = s.substring(start + 1, end).trim();
        }
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return s.substring(first, last + 1).trim();
        }
        return s;
    }

    private String cacheKey(String userId, AiPlanRequest req, String preferredProvider) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", PLAN_PROMPT_VERSION);
            payload.put("userId", userId);
            payload.put("provider", preferredProvider != null ? preferredProvider : "auto");
            payload.put("request", req);
            return CACHE_PREFIX + HashUtils.sha256Hex(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            return CACHE_PREFIX + HashUtils.sha256Hex(PLAN_PROMPT_VERSION + ":" + userId + ":" + req);
        }
    }

    private String readCachedPlan(String key) {
        if (oneApiProperties.getPlanCacheMinutes() <= 0) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            return cached != null && isUsablePlan(cached) ? cached : null;
        } catch (Exception e) {
            log.debug("读取 AI 计划缓存失败，继续实时生成: {}", e.getMessage());
            return null;
        }
    }

    private void cachePlan(String key, String json) {
        int cacheMinutes = oneApiProperties.getPlanCacheMinutes();
        if (cacheMinutes <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(cacheMinutes));
        } catch (Exception e) {
            log.debug("写入 AI 计划缓存失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isUsablePlan(String rawJson) {
        try {
            Map<String, Object> root = MAPPER.readValue(rawJson, new TypeReference<>() {});
            Object daysRaw = root.get("days");
            if (!(daysRaw instanceof List<?> days) || days.size() != 7) {
                return false;
            }
            for (Object dayRaw : days) {
                if (!(dayRaw instanceof Map<?, ?> day)) return false;
                if (!(day.get("diet") instanceof Map<?, ?>)) return false;
                if (!(day.get("exercise") instanceof Map<?, ?>)) return false;
                if (!(day.get("reminders") instanceof List<?>)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
