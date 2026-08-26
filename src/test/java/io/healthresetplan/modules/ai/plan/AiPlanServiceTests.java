package io.healthresetplan.modules.ai.plan;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPlanServiceTests {

    @Test
    void disabledPreferredProviderIsIgnored() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        OneApiProperties properties = new OneApiProperties();
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                properties,
                usageLimiter);
        String validPlan = validPlan();
        when(oneApiService.completeJsonWithExactProvider(
                anyString(), anyList(), eq("qwen"), anyLong()))
                .thenReturn(new OneApiService.AiCompletion("qwen", validPlan));

        AiPlanResponse response = service.generate("user-1", request("glm"));

        assertEquals("qwen", response.provider());
        verify(oneApiService, never()).completeJsonWithExactProvider(
                anyString(), anyList(), eq("glm"), anyLong());
        verify(oneApiService).completeJsonWithExactProvider(
                anyString(), anyList(), eq("qwen"), anyLong());
    }

    @Test
    void invalidQwenPlanReturnsLocalSafePlanWithoutCrossProviderFallback() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                new OneApiProperties(),
                usageLimiter);
        when(oneApiService.completeJsonWithExactProvider(
                anyString(), anyList(), eq("qwen"), anyLong()))
                .thenReturn(new OneApiService.AiCompletion("qwen", "not-json"));

        AiPlanResponse response = service.generate("user-1", request("doubao"));

        assertEquals("local", response.provider());
        verify(oneApiService, never()).completeJsonWithExactProvider(
                anyString(), anyList(), eq("doubao"), anyLong());
        verify(oneApiService).completeJsonWithExactProvider(
                anyString(), anyList(), eq("qwen"), anyLong());
    }

    @Test
    void allProvidersFailReturnsLocalSafePlan() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        MembershipService membershipService = mock(MembershipService.class);
        AiPlanService service = new AiPlanService(
                oneApiService,
                membershipService,
                new OneApiProperties(),
                usageLimiter);
        when(oneApiService.completeJsonWithExactProvider(
                anyString(), anyList(), anyString(), anyLong()))
                .thenThrow(new BusinessException(50301, "unavailable"));

        AiPlanResponse response = service.generate("user-1", request("doubao"));

        assertEquals("local", response.provider());
        assertEquals(7, countDays(response.rawJson()));
        verify(usageLimiter).release("user-1", AiUsageLimiter.Type.PLAN);
        verify(membershipService, never()).consume(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwen", "glm", "deepseek", "doubao"})
    void commonProviderShapeDifferencesAreNormalized(String provider) {
        OneApiService oneApiService = mock(OneApiService.class);
        OneApiProperties properties = new OneApiProperties();
        properties.setChatOrder(java.util.List.of(provider));
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                properties,
                mock(AiUsageLimiter.class));
        String compactPlan = validPlan()
                .replace("\"summary\":\"7 天运动计划\",", "")
                .replace("\"measurements\":[\"晨起记录体重\"]", "\"measurements\":\"晨起记录体重\"")
                .replace("\"habits\":[\"23:00前准备入睡\"]", "\"habits\":\"23:00前准备入睡\"");
        when(oneApiService.completeJsonWithExactProvider(
                anyString(), anyList(), eq(provider), anyLong()))
                .thenReturn(new OneApiService.AiCompletion(provider, compactPlan));

        AiPlanResponse response = service.generate("user-1", request(provider));

        assertEquals(provider, response.provider());
        assertEquals(7, countDays(response.rawJson()));
    }

    @Test
    void invalidPlanUsesOnlyEnabledProvider() {
        OneApiService oneApiService = mock(OneApiService.class);
        AiPlanService service = new AiPlanService(
                oneApiService,
                mock(MembershipService.class),
                new OneApiProperties(),
                mock(AiUsageLimiter.class));
        when(oneApiService.completeJsonWithExactProvider(
                anyString(), anyList(), anyString(), anyLong()))
                .thenAnswer(invocation -> new OneApiService.AiCompletion(
                        invocation.getArgument(2), "not-json"));

        AiPlanResponse response = service.generate("user-1", request("doubao"));

        assertEquals("local", response.provider());
        verify(oneApiService).completeJsonWithExactProvider(
                anyString(), anyList(), eq("qwen"), anyLong());
        for (String provider : new String[]{"doubao", "glm", "deepseek"}) {
            verify(oneApiService, never()).completeJsonWithExactProvider(
                    anyString(), anyList(), eq(provider), anyLong());
        }
    }

    private int countDays(String rawJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(rawJson).path("days").size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String validPlan() {
        String exercise = """
                {"title":"快走与力量","goal":"改善体能","totalMinutes":30,"intensity":"中低强度","location":"室内或户外","equipment":[],
                "warmup":[{"name":"原地走","durationMinutes":5,"instruction":"逐步加快"}],
                "main":[{"name":"快走","sets":1,"durationMinutes":20,"restSeconds":0,"instruction":"保持可交谈强度"}],
                "cooldown":[{"name":"小腿拉伸","durationMinutes":5,"instruction":"缓慢呼吸"}],
                "safetyNotes":["如胸闷或眩晕立即停止"],
                "alternative":{"condition":"膝部不适","name":"坐姿抬腿","instruction":"减小动作幅度"}}
                """.replaceAll("\n", "");
        StringBuilder days = new StringBuilder();
        String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int index = 0; index < 7; index++) {
            if (index > 0) days.append(',');
            days.append("{\"dayIndex\":").append(index + 1)
                    .append(",\"weekDay\":\"").append(weekDays[index])
                    .append("\",\"exercise\":").append(exercise)
                    .append(",\"measurements\":[\"晨起记录体重\"]")
                    .append(",\"habits\":[\"23:00前准备入睡\"]")
                    .append(",\"reminders\":[\"运动前确认身体状态\"]}");
        }
        return "{\"summary\":\"7 天运动计划\",\"keyFocus\":\"规律执行\",\"riskAlert\":null,\"days\":["
                + days + "]}";
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
                "希望爬三层楼不明显气喘",
                "2026-10-01",
                "normal",
                "light");
    }
}
