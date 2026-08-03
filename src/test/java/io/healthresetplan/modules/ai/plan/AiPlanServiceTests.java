package io.healthresetplan.modules.ai.plan;

import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPlanServiceTests {

    @Test
    void invalidPlanIsRepairedByTheSameProvider() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        OneApiProperties properties = new OneApiProperties();
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                properties,
                usageLimiter);
        String validPlan = """
                {"days":[
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]},
                  {"diet":{},"exercise":{},"reminders":[]}
                ]}
                """;
        when(oneApiService.completeJsonWithProvider(
                anyString(), anyList(), eq("glm"), anyLong()))
                .thenReturn(new OneApiService.AiCompletion("glm", "{\"days\":[]}"))
                .thenReturn(new OneApiService.AiCompletion("glm", validPlan));

        AiPlanResponse response = service.generate("user-1", request("glm"));

        assertEquals("glm", response.provider());
        assertEquals(validPlan.trim(), response.rawJson());
        verify(oneApiService, times(2)).completeJsonWithProvider(
                anyString(), anyList(), eq("glm"), anyLong());
    }

    private AiPlanRequest request(String provider) {
        return new AiPlanRequest(
                provider,
                30,
                "male",
                175,
                70,
                22.9,
                "",
                "120/80",
                null,
                null,
                null,
                "general",
                "normal",
                "light");
    }
}
