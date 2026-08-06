package io.healthresetplan.modules.ai.plan;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        String validPlan = validPlan();
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

    @Test
    void invalidPlanFallsBackToAnotherProvider() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                new OneApiProperties(),
                usageLimiter);
        String validPlan = validPlan();
        when(oneApiService.completeJsonWithProvider(
                anyString(), anyList(), eq("doubao"), anyLong()))
                .thenReturn(new OneApiService.AiCompletion("doubao", "not-json"));
        when(oneApiService.completeJsonWithProvider(
                anyString(), anyList(), eq("qwen"), anyLong()))
                .thenReturn(new OneApiService.AiCompletion("qwen", validPlan));

        AiPlanResponse response = service.generate("user-1", request("doubao"));

        assertEquals("qwen", response.provider());
        verify(oneApiService, times(2)).completeJsonWithProvider(
                anyString(), anyList(), eq("doubao"), anyLong());
        verify(oneApiService).completeJsonWithProvider(
                anyString(), anyList(), eq("qwen"), anyLong());
    }

    @Test
    void allProvidersFailAndUsageIsReleased() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                new OneApiProperties(),
                usageLimiter);
        when(oneApiService.completeJsonWithProvider(
                anyString(), anyList(), anyString(), anyLong()))
                .thenThrow(new BusinessException(50301, "unavailable"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.generate("user-1", request("doubao")));

        assertEquals(50302, error.getCode());
        verify(usageLimiter).release("user-1", AiUsageLimiter.Type.PLAN);
    }

    private String validPlan() {
        return """
                {"summary":"健康计划","keyFocus":"规律执行","riskAlert":null,"targetCalories":1800,"days":[
                  {"dayIndex":1,"weekDay":"周一","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":2,"weekDay":"周二","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":3,"weekDay":"周三","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":4,"weekDay":"周四","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":5,"weekDay":"周五","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":6,"weekDay":"周六","diet":{},"exercise":{},"reminders":[]},
                  {"dayIndex":7,"weekDay":"周日","diet":{},"exercise":{},"reminders":[]}
                ]}
                """;
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
