package io.healthresetplan.modules.membership;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

final class AiEntitlementPolicy {

    enum Tier { FREE, TRIAL, VIP }
    enum Period { DAY, WEEK, MONTH, TRIAL }

    record Quota(String bucket, Period period, int limit) {}

    private static final Map<String, Quota> FREE = quotas(
            entry("meal_analysis", Period.DAY, 3),
            entry("ai_chat", Period.DAY, 1),
            entry("weekly_report", Period.WEEK, 1));

    private static final Map<String, Quota> TRIAL = quotas(
            entry("meal_analysis", Period.DAY, 3),
            entry("ai_chat", Period.DAY, 3),
            entry("meal_swap", Period.DAY, 3),
            entry("personalized_menu", Period.WEEK, 1),
            entry("ai_plan", Period.WEEK, 1),
            entry("weekly_report", Period.WEEK, 1),
            entry("report_ocr", Period.TRIAL, 1),
            entry("ai_vision_skin", Period.TRIAL, 1),
            entry("ai_vision_tongue", Period.TRIAL, 1),
            entry("ai_vision_hair", Period.TRIAL, 1));

    private static final Map<String, Quota> VIP = quotas(
            entry("meal_analysis", Period.DAY, 3),
            entry("ai_chat", Period.DAY, 10),
            entry("meal_swap", Period.DAY, 3),
            entry("personalized_menu", Period.WEEK, 1),
            entry("ai_plan", Period.WEEK, 1),
            entry("weekly_report", Period.WEEK, 1),
            entry("report_ocr", Period.MONTH, 3),
            sharedEntry("ai_vision_skin", "ai_vision", Period.MONTH, 6),
            sharedEntry("ai_vision_tongue", "ai_vision", Period.MONTH, 6),
            sharedEntry("ai_vision_hair", "ai_vision", Period.MONTH, 6));

    private AiEntitlementPolicy() {}

    static Quota quota(Tier tier, String feature) {
        return switch (tier) {
            case FREE -> FREE.get(feature);
            case TRIAL -> TRIAL.get(feature);
            case VIP -> VIP.get(feature);
        };
    }

    static String periodKey(Quota quota, LocalDate today, LocalDate trialStart) {
        return switch (quota.period()) {
            case DAY -> "day:" + today;
            case WEEK -> "week:" + today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> "month:" + YearMonth.from(today);
            case TRIAL -> "trial:" + trialStart;
        };
    }

    private static Map.Entry<String, Quota> entry(String feature, Period period, int limit) {
        return sharedEntry(feature, feature, period, limit);
    }

    private static Map.Entry<String, Quota> sharedEntry(
            String feature, String bucket, Period period, int limit) {
        return Map.entry(feature, new Quota(bucket, period, limit));
    }

    @SafeVarargs
    private static Map<String, Quota> quotas(Map.Entry<String, Quota>... entries) {
        Map<String, Quota> result = new LinkedHashMap<>();
        for (Map.Entry<String, Quota> entry : entries) result.put(entry.getKey(), entry.getValue());
        return Map.copyOf(result);
    }
}
