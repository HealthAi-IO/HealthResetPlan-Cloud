package io.healthresetplan.modules.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.data.UserDataService;
import io.healthresetplan.modules.data.dto.UserDataResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiHealthContextServiceTests {

    @Test
    void buildsCompactContextFromOnlineHealthData() {
        UserDataService userDataService = mock(UserDataService.class);
        long now = System.currentTimeMillis();
        when(userDataService.load("user-1")).thenReturn(new UserDataResponse(3, Map.of(
                "user_profile", List.of(Map.of(
                        "birth_year", 1990,
                        "gender", "female",
                        "height_cm", 165,
                        "weight_kg", 60,
                        "diet_preference", "light")),
                "health_indicator", List.of(Map.of(
                        "type", "bp",
                        "measured_at", now,
                        "payload_json", "{\"systolic\":128,\"diastolic\":82}")),
                "meal_record", List.of(Map.of(
                        "meal_type", "lunch",
                        "name", "杂粮饭",
                        "eaten_at", now,
                        "total_calories", 520)),
                "plan", List.of(Map.of(
                        "type", "exercise",
                        "plan_date", now,
                        "payload_json", "{\"summary\":\"饭后步行30分钟\"}")),
                "ai_memory", List.of(Map.of(
                        "content", "不吃海鲜",
                        "enabled", 1,
                        "updated_at", now))
        )));
        AiHealthContextService service = new AiHealthContextService(userDataService, new ObjectMapper());

        AiHealthContextService.HealthContext result = service.build("user-1", true);

        assertTrue(result.prompt().contains("不吃海鲜"));
        assertTrue(result.prompt().contains("杂粮饭"));
        assertTrue(result.prompt().contains("饭后步行30分钟"));
        assertTrue(result.sources().contains("近30天健康指标"));
        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                result.prompt().substring(result.prompt().indexOf("bp(") + 3,
                        result.prompt().indexOf("bp(") + 13));
    }

    @Test
    void genericModeDoesNotLoadPersonalData() {
        UserDataService userDataService = mock(UserDataService.class);
        AiHealthContextService service = new AiHealthContextService(userDataService, new ObjectMapper());

        AiHealthContextService.HealthContext result = service.build("user-1", false);

        assertTrue(result.prompt().isEmpty());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void mealQuestionUsesYesterdayAndIncludesFoodDetails() {
        UserDataService userDataService = mock(UserDataService.class);
        long today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        when(userDataService.load("user-1")).thenReturn(new UserDataResponse(1, Map.of(
                "meal_record", List.of(
                        Map.of("meal_type", "lunch", "name", "昨天午餐", "eaten_at", today - 3_600_000,
                                "total_calories", 500, "foods_json", "[{\"name\":\"鸡胸肉\"}]"),
                        Map.of("meal_type", "dinner", "name", "今天晚餐", "eaten_at", today + 3_600_000,
                                "total_calories", 700))
        )));
        AiHealthContextService service = new AiHealthContextService(userDataService, new ObjectMapper());

        AiHealthContextService.HealthContext result = service.build("user-1", true, "我昨天午餐吃了什么？");

        assertTrue(result.prompt().contains("昨天午餐"));
        assertTrue(result.prompt().contains("鸡胸肉"));
        assertTrue(!result.prompt().contains("今天晚餐"));
        assertTrue(result.sources().contains("昨天饮食记录"));
    }

    @Test
    void indicatorQuestionBuildsWeeklyStatistics() {
        UserDataService userDataService = mock(UserDataService.class);
        long today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        when(userDataService.load("user-1")).thenReturn(new UserDataResponse(1, Map.of(
                "health_indicator", List.of(
                        Map.of("type", "weight", "measured_at", today - 2 * 86_400_000L,
                                "payload_json", "{\"weightKg\": 80}"),
                        Map.of("type", "weight", "measured_at", today - 86_400_000L,
                                "payload_json", "{\"weightKg\": 79}"))
        )));
        AiHealthContextService service = new AiHealthContextService(userDataService, new ObjectMapper());

        AiHealthContextService.HealthContext result =
                service.build("user-1", true, "最近30天体重平均是多少，变化趋势如何？");

        assertTrue(result.prompt().contains("最近30天健康指标统计"));
        assertTrue(result.prompt().contains("记录2次"));
        assertTrue(result.prompt().contains("平均79.5"));
        assertTrue(result.prompt().contains("变化+1"));
    }
}
