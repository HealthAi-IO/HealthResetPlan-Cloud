package io.healthresetplan.modules.ai.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.MedicalRiskGuard;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AiPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是谨慎的运动健康指导助手。根据用户档案生成循序渐进的 7 天运动计划。
            只输出可 JSON.parse 的纯 JSON，不要 Markdown，不生成饮食、药物或诊断建议。
            必须完全符合结构：
            {
              "summary":"本周运动安排概述",
              "keyFocus":"本周训练重点",
              "riskAlert":null,
              "days":[{
                "dayIndex":1,
                "weekDay":"周一",
                "exercise":{
                  "title":"低冲击全身训练",
                  "goal":"提升心肺与活动度",
                  "totalMinutes":35,
                  "intensity":"低强度",
                  "location":"居家",
                  "equipment":["椅子"],
                  "warmup":[{"name":"原地踏步","durationMinutes":5,"instruction":"自然摆臂，保持顺畅呼吸"}],
                  "main":[{"name":"椅子深蹲","sets":3,"reps":"10次","durationMinutes":0,"restSeconds":45,"instruction":"膝盖朝脚尖方向，动作缓慢"}],
                  "cooldown":[{"name":"小腿拉伸","durationMinutes":4,"instruction":"左右各保持30秒"}],
                  "safetyNotes":["出现胸痛、明显气短或头晕立即停止"],
                  "alternative":{"condition":"膝部不适","name":"坐姿抬腿","instruction":"每侧10次，动作无痛为准"}
                },
                "measurements":["晨起、早餐前记录体重"],
                "habits":["23:00 前准备入睡"],
                "reminders":["运动前确认当天身体状态"]
              }]
            }
            约束：
            1. days 正好 7 条，dayIndex 为 1-7；至少安排 1 天主动恢复，不连续安排高强度训练。
            2. 每天必须包含热身、主训练、放松、安全提示和替代动作；动作给出组次或时长、休息和要点。
            3. 每天提供 measurements 和 habits 数组：测量项目结合用户档案与近期指标，生活习惯必须具体可执行；不生成饮食计划。
            4. 强度只能是低强度或中等强度；结合用户运动基础、BMI、健康史和近期指标，优先低冲击动作。
            5. 不承诺减重效果，不推荐极端训练，不为急性不适或禁忌人群强行安排运动。
            """;

    private final OneApiService oneApiService;
    private final MembershipService membershipService;
    private final OneApiProperties oneApiProperties;
    private final AiUsageLimiter usageLimiter;

    public AiPlanService(OneApiService oneApiService,
                         MembershipService membershipService,
                         OneApiProperties oneApiProperties,
                         AiUsageLimiter usageLimiter) {
        this.oneApiService = oneApiService;
        this.membershipService = membershipService;
        this.oneApiProperties = oneApiProperties;
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
                : "qwen";
        usageLimiter.consume(userId, AiUsageLimiter.Type.PLAN);
        try {
            long maxCompletionTokens = Math.max(1200L, oneApiProperties.getPlanMaxCompletionTokens());
            for (String provider : planProviders(preferredProvider)) {
                try {
                    AiPlanResponse response = generateWithProvider(
                            userId, messages, provider, maxCompletionTokens);
                    if (response != null) return response;
                } catch (BusinessException e) {
                    log.warn("AI plan provider failed userId={} provider={} code={}",
                            userId, provider, e.getCode());
                }
            }
            log.warn("All AI plan providers failed userId={}, using local safe plan", userId);
            return new AiPlanResponse("local", localSafePlan(req), 0, 0);
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.PLAN);
            throw e;
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private AiPlanResponse generateWithProvider(
            String userId,
            List<ChatCompletionMessageParam> messages,
            String provider,
            long maxCompletionTokens) {
        OneApiService.AiCompletion completion = oneApiService.completeJsonWithExactProvider(
                userId, messages, provider, maxCompletionTokens);
        String rawJson = extractJson(completion.content());
        String validationError = planValidationError(rawJson);
        if (validationError == null) {
            log.info("AI 计划生成成功 provider={}", completion.provider());
            return new AiPlanResponse(completion.provider(), rawJson, 0, 0);
        }

        String normalized = normalizePlan(rawJson);
        if (normalized != null && planValidationError(normalized) == null) {
            log.info("AI 计划本地归一化成功 provider={} reason={}",
                    completion.provider(), validationError);
            return new AiPlanResponse(completion.provider(), normalized, 0, 0);
        }

        log.warn("AI plan JSON invalid userId={} provider={} reason={}, trying fallback provider",
                userId, completion.provider(), validationError);
        return null;
    }

    private String normalizePlan(String rawJson) {
        try {
            JsonNode parsed = MAPPER.readTree(rawJson);
            if (!(parsed instanceof ObjectNode root)) return null;
            putTextIfMissing(root, "summary", "未来 7 天循序渐进运动安排");
            putTextIfMissing(root, "keyFocus", "安全、规律地完成每日活动");
            if (!root.has("riskAlert")) root.putNull("riskAlert");

            JsonNode daysNode = first(root, "days", "weeklyPlan", "plan");
            if (!(daysNode instanceof ArrayNode days) || days.size() != 7) return null;
            root.set("days", days);
            String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            for (int index = 0; index < days.size(); index++) {
                if (!(days.get(index) instanceof ObjectNode day)) return null;
                day.put("dayIndex", index + 1);
                putTextIfMissing(day, "weekDay", weekDays[index]);
                JsonNode exerciseNode = first(day, "exercise", "exercisePlan", "training");
                if (!(exerciseNode instanceof ObjectNode exercise)) return null;
                day.set("exercise", exercise);
                normalizeExercise(exercise);
                normalizeStringArray(day, "measurements", "晨起后记录体重或当日重点指标");
                normalizeStringArray(day, "habits", "按固定时间准备入睡，保证恢复");
                normalizeStringArray(day, "reminders", "运动前确认当天身体状态");
                ArrayNode reminders = (ArrayNode) day.get("reminders");
                while (reminders.size() > 2) reminders.remove(reminders.size() - 1);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void normalizeExercise(ObjectNode exercise) {
        putTextIfMissing(exercise, "title", "低冲击全身活动");
        putTextIfMissing(exercise, "goal", "提升活动度与基础体能");
        putNumberIfMissing(exercise, "totalMinutes", 30);
        putTextIfMissing(exercise, "intensity", "低强度");
        putTextIfMissing(exercise, "location", "居家或户外");
        normalizeStringArray(exercise, "equipment", "无需器械");
        normalizeSteps(exercise, "warmup", false, "原地踏步", 5, "自然摆臂，逐步进入运动状态");
        normalizeSteps(exercise, "main", true, "舒适速度步行", 20, "保持能够正常交谈的强度");
        normalizeSteps(exercise, "cooldown", false, "全身放松", 5, "放慢呼吸，轻柔拉伸");
        normalizeStringArray(exercise, "safetyNotes", "胸痛、明显气短或头晕时立即停止");
        JsonNode alternativeNode = exercise.get("alternative");
        ObjectNode alternative;
        if (alternativeNode instanceof ObjectNode value) {
            alternative = value;
        } else {
            alternative = MAPPER.createObjectNode();
            if (alternativeNode != null && alternativeNode.isTextual()) {
                alternative.put("instruction", alternativeNode.asText());
            }
            exercise.set("alternative", alternative);
        }
        putTextIfMissing(alternative, "condition", "当天状态不佳或关节不适");
        putTextIfMissing(alternative, "name", "坐姿抬腿与呼吸练习");
        putTextIfMissing(alternative, "instruction", "减小幅度，以无痛和呼吸顺畅为准");
    }

    private void normalizeSteps(ObjectNode parent, String field, boolean main,
                                String defaultName, int defaultMinutes, String defaultInstruction) {
        JsonNode raw = parent.get(field);
        ArrayNode steps = MAPPER.createArrayNode();
        if (raw instanceof ArrayNode array) {
            for (JsonNode item : array) {
                ObjectNode step = MAPPER.createObjectNode();
                if (item instanceof ObjectNode object) {
                    step.setAll(object);
                } else if (item.isTextual()) {
                    step.put("name", item.asText());
                }
                putTextIfMissing(step, "name", defaultName);
                putTextIfMissing(step, "instruction", defaultInstruction);
                putNumberIfMissing(step, "durationMinutes", defaultMinutes);
                if (main) {
                    putNumberIfMissing(step, "sets", 1);
                    putNumberIfMissing(step, "restSeconds", 30);
                }
                steps.add(step);
            }
        } else if (raw != null && raw.isTextual()) {
            ObjectNode step = MAPPER.createObjectNode();
            step.put("name", raw.asText());
            step.put("instruction", defaultInstruction);
            step.put("durationMinutes", defaultMinutes);
            if (main) step.put("sets", 1);
            steps.add(step);
        }
        if (steps.isEmpty()) {
            ObjectNode step = MAPPER.createObjectNode();
            step.put("name", defaultName);
            step.put("instruction", defaultInstruction);
            step.put("durationMinutes", defaultMinutes);
            if (main) {
                step.put("sets", 1);
                step.put("restSeconds", 30);
            }
            steps.add(step);
        }
        parent.set(field, steps);
    }

    private void normalizeStringArray(ObjectNode parent, String field, String fallback) {
        JsonNode raw = parent.get(field);
        ArrayNode values = MAPPER.createArrayNode();
        if (raw instanceof ArrayNode array) {
            for (JsonNode item : array) {
                String value = item.isTextual() ? item.asText().trim() : item.asText("").trim();
                if (!value.isEmpty()) values.add(value);
            }
        } else if (raw != null && raw.isTextual() && !raw.asText().isBlank()) {
            values.add(raw.asText().trim());
        }
        if (values.isEmpty()) values.add(fallback);
        parent.set(field, values);
    }

    private JsonNode first(ObjectNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private void putTextIfMissing(ObjectNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            node.put(field, fallback);
        }
    }

    private void putNumberIfMissing(ObjectNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) node.put(field, fallback);
    }

    private String localSafePlan(AiPlanRequest req) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("summary", "根据当前档案生成的 7 天基础运动与测量计划");
        root.put("keyFocus", "从低冲击活动开始，逐步建立规律");
        root.putNull("riskAlert");
        ArrayNode days = root.putArray("days");
        String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String[] titles = {"低冲击全身启动", "步行与下肢力量", "主动恢复", "核心稳定训练", "节奏步行", "全身基础力量", "舒缓恢复与复盘"};
        for (int index = 0; index < 7; index++) {
            ObjectNode day = days.addObject();
            day.put("dayIndex", index + 1);
            day.put("weekDay", weekDays[index]);
            ObjectNode exercise = day.putObject("exercise");
            exercise.put("title", titles[index]);
            exercise.put("goal", index == 2 || index == 6 ? "促进恢复并保持活动" : "提升活动度与基础体能");
            exercise.put("totalMinutes", index == 2 || index == 6 ? 20 : 30);
            exercise.put("intensity", "低强度");
            exercise.put("location", "居家或户外");
            exercise.putArray("equipment").add("无需器械");
            normalizeSteps(exercise, "warmup", false, "原地踏步", 5, "自然摆臂，逐步进入运动状态");
            normalizeSteps(exercise, "main", true,
                    index == 2 || index == 6 ? "舒缓步行与关节活动" : "舒适速度步行配合坐站练习",
                    index == 2 || index == 6 ? 10 : 20,
                    "保持能够正常交谈的强度，动作缓慢稳定");
            normalizeSteps(exercise, "cooldown", false, "全身放松", 5, "放慢呼吸，轻柔拉伸");
            exercise.putArray("safetyNotes").add("胸痛、明显气短或头晕时立即停止并及时就医");
            ObjectNode alternative = exercise.putObject("alternative");
            alternative.put("condition", "当天状态不佳或关节不适");
            alternative.put("name", "坐姿抬腿与呼吸练习");
            alternative.put("instruction", "减小动作幅度，以无痛和呼吸顺畅为准");
            day.putArray("measurements").add(index == 0 ? "晨起后记录体重" : "运动前记录精神和疲劳状态");
            day.putArray("habits").add("按固定时间准备入睡，保证恢复");
            day.putArray("reminders").add("运动前确认当天身体状态");
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(50302, "计划生成失败，请稍后重试");
        }
    }

    private List<String> planProviders(String preferredProvider) {
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        List<String> enabledProviders = oneApiProperties.getChatOrder();
        if (preferredProvider != null && enabledProviders.contains(preferredProvider)) {
            providers.add(preferredProvider);
        }
        providers.addAll(enabledProviders);
        providers.removeIf(provider -> provider == null || provider.isBlank());
        return List.copyOf(providers);
    }

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
            case "lose_weight", "fat_loss" -> "减重控脂";
            case "muscle_gain" -> "增强肌力和肌肉耐力";
            case "improve_fitness" -> "增强体能和心肺耐力";
            case "sleep_better" -> "改善睡眠和日间精力";
            case "lower_bp", "bp_control" -> "血压管理";
            case "lower_glucose", "glucose_control" -> "血糖控制";
            case "lower_lipid" -> "血脂调节";
            case "quit_smoking" -> "戒烟期间保持活动与稳定作息";
            default -> "综合健康管理";
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
                .append("；运动基础=").append(exercise)
                .append("。");
        if (req.goalDetail() != null && !req.goalDetail().isBlank()) {
            sb.append("用户希望达到的具体状态=").append(req.goalDetail().trim()).append("。");
        }
        if (req.targetDate() != null && !req.targetDate().isBlank()) {
            sb.append("期望达成日期=").append(req.targetDate().trim()).append("。");
        }
        sb.append("把长期目标拆解为接下来 7 天可执行、循序渐进的运动、测量和生活习惯安排；不生成饮食计划。只输出规定 JSON。");
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

    private String planValidationError(String rawJson) {
        try {
            Map<String, Object> root = MAPPER.readValue(rawJson, new TypeReference<>() {});
            if (!(root.get("summary") instanceof String)) return "summary-invalid";
            if (!(root.get("keyFocus") instanceof String)) return "key-focus-invalid";
            Object riskAlert = root.get("riskAlert");
            if (riskAlert != null && !(riskAlert instanceof String)) return "risk-alert-invalid";
            Object daysRaw = root.get("days");
            if (!(daysRaw instanceof List<?> days)) {
                return "days-not-array";
            }
            if (days.size() != 7) {
                return "days-size-" + days.size();
            }
            for (int i = 0; i < days.size(); i++) {
                Object dayRaw = days.get(i);
                if (!(dayRaw instanceof Map<?, ?> day)) return "day-" + i + "-not-object";
                if (!(day.get("dayIndex") instanceof Number)) return "day-" + i + "-index-invalid";
                if (!(day.get("weekDay") instanceof String)) return "day-" + i + "-weekday-invalid";
                if (!(day.get("exercise") instanceof Map<?, ?> exercise)) return "day-" + i + "-exercise-invalid";
                if (!(exercise.get("title") instanceof String)
                        || !(exercise.get("goal") instanceof String)
                        || !(exercise.get("totalMinutes") instanceof Number)
                        || !(exercise.get("intensity") instanceof String)
                        || !(exercise.get("location") instanceof String)
                        || !(exercise.get("equipment") instanceof List<?> equipment)
                        || equipment.stream().anyMatch(item -> !(item instanceof String))
                        || !validExerciseSteps(exercise.get("warmup"), false)
                        || !validExerciseSteps(exercise.get("main"), true)
                        || !validExerciseSteps(exercise.get("cooldown"), false)
                        || !(exercise.get("safetyNotes") instanceof List<?> safetyNotes)
                        || safetyNotes.isEmpty()
                        || safetyNotes.stream().anyMatch(item -> !(item instanceof String))
                        || !(exercise.get("alternative") instanceof Map<?, ?> alternative)
                        || !(alternative.get("name") instanceof String)
                        || !(alternative.get("instruction") instanceof String)) {
                    return "day-" + i + "-exercise-detail-invalid";
                }
                if (!(day.get("reminders") instanceof List<?> reminders)
                        || reminders.size() > 2
                        || reminders.stream().anyMatch(item -> !(item instanceof String))) {
                    return "day-" + i + "-reminders-invalid";
                }
                if (!(day.get("measurements") instanceof List<?> measurements)
                        || measurements.isEmpty()
                        || measurements.stream().anyMatch(item -> !(item instanceof String))) {
                    return "day-" + i + "-measurements-invalid";
                }
                if (!(day.get("habits") instanceof List<?> habits)
                        || habits.isEmpty()
                        || habits.stream().anyMatch(item -> !(item instanceof String))) {
                    return "day-" + i + "-habits-invalid";
                }
            }
            return null;
        } catch (Exception e) {
            return "invalid-json";
        }
    }

    private boolean validExerciseSteps(Object raw, boolean main) {
        if (!(raw instanceof List<?> steps) || steps.isEmpty()) return false;
        for (Object item : steps) {
            if (!(item instanceof Map<?, ?> step)
                    || !(step.get("name") instanceof String)
                    || !(step.get("instruction") instanceof String)) {
                return false;
            }
            if (main && !(step.get("sets") instanceof Number)
                    && !(step.get("durationMinutes") instanceof Number)) {
                return false;
            }
        }
        return true;
    }
}
