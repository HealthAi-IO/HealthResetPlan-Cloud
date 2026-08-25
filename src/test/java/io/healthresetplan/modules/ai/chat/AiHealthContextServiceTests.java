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
}
