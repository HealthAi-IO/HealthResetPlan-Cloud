package io.healthresetplan.modules.ai.wellness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiWellnessService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MENU_PROMPT = """
            你是谨慎的家庭营养规划助手。根据用户档案生成从指定日期开始的7天菜单。
            只输出可解析的纯JSON，不输出Markdown。不得诊断疾病，不得改变药物，不得生成极端节食方案。
            过敏食物绝对不能出现；不喜欢的食物尽量避免；每道菜食材必须给出克数。
            根对象结构：
            {"summary":"","keyFocus":"","targetCalories":1800,"days":[
              {"dayIndex":1,"date":"YYYY-MM-DD","weekDay":"周一","meals":{
                "breakfast":{"name":"","ingredients":["食材 100g"],"calories":0,"proteinG":0,"carbsG":0,"fatG":0},
                "lunch":{"name":"","ingredients":[],"calories":0,"proteinG":0,"carbsG":0,"fatG":0},
                "dinner":{"name":"","ingredients":[],"calories":0,"proteinG":0,"carbsG":0,"fatG":0},
                "snack":{"name":"","ingredients":[],"calories":0,"proteinG":0,"carbsG":0,"fatG":0}
              }}
            ]}
            days必须正好7条，dayIndex为1到7。热量和营养值是合理估算，用户仍需确认实际份量。
            """;

    private static final String SWAP_PROMPT = """
            你是家庭营养规划助手。替换指定的一餐，保持与原餐相近的热量和三大营养素，避开过敏及不喜欢的食物。
            只输出纯JSON对象：
            {"name":"","ingredients":["食材 100g"],"calories":0,"proteinG":0,"carbsG":0,"fatG":0}
            不得诊断疾病，不得提供药物建议。
            """;

    private static final String REPORT_PROMPT = """
            你是健康记录解释助手。数值统计已经由程序计算，你只能基于输入统计进行归纳，不得虚构数据或诊断疾病。
            数据不足时必须明确说明。建议应温和、具体、可执行，不得建议用户自行停药或改变剂量。
            只输出纯JSON：
            {"title":"最近7天健康周报","summary":"","dataQuality":{"level":"good|partial","message":""},
             "wins":[""],"concerns":[""],
             "actions":[{"title":"","detail":"","planType":"meal|exercise|measurement"}],
             "metrics":[{"label":"","value":"","trend":"up|down|stable|unknown"}]}
            wins最多3条，concerns最多3条，actions正好3条，metrics最多6条。
            """;

    private final OneApiService oneApiService;
    private final AiUsageLimiter usageLimiter;
    private final MembershipService membershipService;

    public AiWellnessService(OneApiService oneApiService, AiUsageLimiter usageLimiter,
                             MembershipService membershipService) {
        this.oneApiService = oneApiService;
        this.usageLimiter = usageLimiter;
        this.membershipService = membershipService;
    }

    public AiWellnessResponse generateMenu(String userId, PersonalizedMenuRequest request) {
        if (request.targetCalories() < 1000 || request.targetCalories() > 4000) {
            throw new BusinessException(40001, "请先完善健康档案，再生成个性化菜单");
        }
        String input = toJson(request);
        return complete(userId, request.provider(), AiUsageLimiter.Type.PLAN,
                "personalized_menu", MENU_PROMPT, input, 6000, this::validMenu);
    }

    public AiWellnessResponse swapMeal(String userId, MenuSwapRequest request) {
        String input = toJson(request);
        return complete(userId, request.provider(), AiUsageLimiter.Type.PLAN,
                "meal_swap", SWAP_PROMPT, input, 1600, this::validMeal);
    }

    public AiWellnessResponse generateWeeklyReport(
            String userId,
            WeeklyHealthReportRequest request) {
        String input = toJson(request);
        return complete(userId, request.provider(), AiUsageLimiter.Type.REPORT,
                "weekly_report", REPORT_PROMPT, input, 2800, this::validReport);
    }

    private AiWellnessResponse complete(
            String userId,
            String provider,
            AiUsageLimiter.Type usageType,
            String featureCode,
            String systemPrompt,
            String input,
            long maxTokens,
            java.util.function.Predicate<Map<String, Object>> validator) {
        usageLimiter.consume(userId, usageType);
        try {
            OneApiService.AiCompletion completion = oneApiService.completeJsonWithProvider(
                    userId,
                    List.of(
                            OneApiService.systemMsg(systemPrompt),
                            OneApiService.userMsg(input)
                    ),
                    provider == null || provider.isBlank() ? "qwen" : provider,
                    maxTokens);
            Map<String, Object> data = parseObject(completion.content());
            if (!validator.test(data)) {
                throw new BusinessException(50302, "AI返回的数据格式不完整，请重试或切换模型");
            }
            if (membershipService.billingEnabled() && !membershipService.consume(userId, featureCode)) {
                throw new BusinessException(42901, "AI 次数不足，请购买 AI 健康分析包");
            }
            return new AiWellnessResponse(completion.provider(), data);
        } catch (RuntimeException error) {
            usageLimiter.release(userId, usageType);
            throw error;
        }
    }

    private boolean validMenu(Map<String, Object> data) {
        Object rawDays = data.get("days");
        if (!(rawDays instanceof List<?> days) || days.size() != 7) return false;
        for (Object item : days) {
            if (!(item instanceof Map<?, ?> day)
                    || !(day.get("dayIndex") instanceof Number)
                    || !(day.get("meals") instanceof Map<?, ?> meals)
                    || !validMealMap(meals.get("breakfast"))
                    || !validMealMap(meals.get("lunch"))
                    || !validMealMap(meals.get("dinner"))) {
                return false;
            }
        }
        return true;
    }

    private boolean validMeal(Map<String, Object> data) {
        return validMealMap(data);
    }

    private boolean validMealMap(Object raw) {
        if (!(raw instanceof Map<?, ?> meal)) return false;
        return meal.get("name") instanceof String
                && meal.get("ingredients") instanceof List<?>
                && meal.get("calories") instanceof Number;
    }

    private boolean validReport(Map<String, Object> data) {
        return data.get("summary") instanceof String
                && data.get("dataQuality") instanceof Map<?, ?>
                && data.get("wins") instanceof List<?>
                && data.get("concerns") instanceof List<?>
                && data.get("actions") instanceof List<?> actions
                && actions.size() == 3;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception error) {
            throw new BusinessException(40001, "请求数据格式错误");
        }
    }

    private Map<String, Object> parseObject(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            int first = value.indexOf('{');
            int last = value.lastIndexOf('}');
            if (first >= 0 && last > first) value = value.substring(first, last + 1);
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception error) {
            throw new BusinessException(50302, "AI返回的数据无法解析，请重试或切换模型");
        }
    }
}
