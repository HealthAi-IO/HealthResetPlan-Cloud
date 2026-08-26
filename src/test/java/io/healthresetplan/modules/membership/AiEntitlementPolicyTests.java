package io.healthresetplan.modules.membership;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiEntitlementPolicyTests {

    @Test
    void freeTierKeepsDailyMealsChatAndWeeklyReport() {
        assertEquals(3, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.FREE, "meal_analysis").limit());
        assertEquals(1, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.FREE, "ai_chat").limit());
        assertEquals(1, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.FREE, "weekly_report").limit());
        assertNull(AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.FREE, "report_ocr"));
    }

    @Test
    void trialGivesEachSelfCheckTypeOneUse() {
        assertEquals(1, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.TRIAL, "ai_vision_skin").limit());
        assertEquals(1, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.TRIAL, "ai_vision_tongue").limit());
        assertEquals(1, AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.TRIAL, "ai_vision_hair").limit());
    }

    @Test
    void vipSelfChecksShareOneMonthlyBucket() {
        var skin = AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.VIP, "ai_vision_skin");
        var hair = AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.VIP, "ai_vision_hair");
        assertEquals("ai_vision", skin.bucket());
        assertEquals(skin, hair);
        assertEquals(6, skin.limit());
    }

    @Test
    void weeklyPeriodStartsOnMonday() {
        var quota = AiEntitlementPolicy.quota(
                AiEntitlementPolicy.Tier.VIP, "weekly_report");
        assertEquals("week:2026-08-24", AiEntitlementPolicy.periodKey(
                quota, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 20)));
    }
}
