package io.healthresetplan.modules.ai.oneapi;

import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.wellness.AiWellnessResponse;
import io.healthresetplan.modules.ai.wellness.AiWellnessService;
import io.healthresetplan.modules.ai.wellness.PersonalizedMenuRequest;
import io.healthresetplan.modules.ai.wellness.WeeklyHealthReportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiWellnessLiveSmokeTests {

    @Test
    void configuredProviderGeneratesMenuAndWeeklyReport() {
        String apiKey = System.getenv("AI_CHAT_QWEN_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank());

        OneApiProperties properties = new OneApiProperties();
        OneApiProperties.ProviderConfig qwen = new OneApiProperties.ProviderConfig();
        qwen.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        qwen.setApiKey(apiKey);
        qwen.setModel("qwen3.7-plus");
        properties.setProviders(Map.of("qwen", qwen));
        properties.setChatOrder(List.of("qwen"));
        properties.setTimeoutSeconds(180);

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        OneApiService oneApi = new OneApiService(properties, jdbc);
        oneApi.reloadClients();
        AiWellnessService service = new AiWellnessService(
                oneApi,
                mock(AiUsageLimiter.class)
        );

        AiWellnessResponse menu = service.generateMenu("smoke-user", new PersonalizedMenuRequest(
                "qwen", 40, "female", 165, 60, "", "", "maintain", "均衡三餐", "normal",
                List.of(), List.of(), null, 30, List.of("炒锅"), 1800, 80, 220, 55,
                "2026-08-11"
        ));
        assertEquals(7, ((List<?>) menu.data().get("days")).size());

        AiWellnessResponse report = service.generateWeeklyReport("smoke-user", new WeeklyHealthReportRequest(
                "qwen", "2026-08-05", "2026-08-11", 3,
                Map.of("mealDays", 2, "exerciseDays", 1, "completedCheckIns", 4)
        ));
        assertFalse(report.data().get("summary").toString().isBlank());
        assertEquals(3, ((List<?>) report.data().get("actions")).size());
    }
}
