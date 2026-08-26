package io.healthresetplan.modules.membership;

import io.healthresetplan.modules.membership.AiEntitlementPolicy.Quota;
import io.healthresetplan.modules.membership.AiEntitlementPolicy.Tier;
import io.healthresetplan.modules.payment.PaymentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiEntitlementService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final PaymentService paymentService;

    public AiEntitlementService(JdbcTemplate jdbc, PaymentService paymentService) {
        this.jdbc = jdbc;
        this.paymentService = paymentService;
    }

    public boolean hasIncluded(String userId, String feature) {
        Context context = context(userId);
        Quota quota = AiEntitlementPolicy.quota(context.tier(), feature);
        if (quota == null) return false;
        return used(userId, quota, context) < quota.limit();
    }

    @Transactional
    public boolean consumeIncluded(String userId, String feature) {
        Context context = context(userId);
        Quota quota = AiEntitlementPolicy.quota(context.tier(), feature);
        if (quota == null) return false;
        String periodKey = periodKey(quota, context);
        jdbc.update("""
                INSERT IGNORE INTO ai_feature_usage
                  (user_id, feature_code, period_key, benefit_source, used_count)
                VALUES (?, ?, ?, ?, 0)
                """, userId, quota.bucket(), periodKey, source(context.tier()));
        int updated = jdbc.update("""
                UPDATE ai_feature_usage SET used_count = used_count + 1
                WHERE user_id = ? AND feature_code = ? AND period_key = ?
                  AND benefit_source = ? AND used_count < ?
                """, userId, quota.bucket(), periodKey, source(context.tier()), quota.limit());
        if (updated == 1 && context.tier() == Tier.VIP) paymentService.markVipBenefitUsed(userId);
        return updated == 1;
    }

    public Map<String, Object> status(String userId) {
        Context context = context(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tier", source(context.tier()));
        result.put("trialActive", context.trialActive());
        result.put("trialStartsAt", context.trialStartsAt());
        result.put("trialExpiresAt", context.trialExpiresAt());
        result.put("vipActive", context.tier() == Tier.VIP);
        result.put("creditBalance", paymentService.creditBalance(userId));
        result.put("benefits", benefits(userId, context));
        return result;
    }

    private List<Map<String, Object>> benefits(String userId, Context context) {
        return List.of(
                benefit(userId, context, "meal_analysis", "三餐识别"),
                benefit(userId, context, "ai_chat", "健康管家会话"),
                benefit(userId, context, "meal_swap", "AI 换餐"),
                benefit(userId, context, "personalized_menu", "个性化菜单"),
                benefit(userId, context, "ai_plan", "运动计划"),
                benefit(userId, context, "weekly_report", "健康周报"),
                benefit(userId, context, "report_ocr", "报告识别"),
                benefit(userId, context, "ai_vision_skin", "皮肤分析"),
                benefit(userId, context, "ai_vision_tongue", "舌象分析"),
                benefit(userId, context, "ai_vision_hair", "头发分析"));
    }

    private Map<String, Object> benefit(
            String userId, Context context, String feature, String label) {
        Quota quota = AiEntitlementPolicy.quota(context.tier(), feature);
        if (quota == null) {
            return Map.of("feature", feature, "label", label, "included", false);
        }
        int used = used(userId, quota, context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feature", feature);
        result.put("label", label);
        result.put("included", true);
        result.put("period", quota.period().name().toLowerCase());
        result.put("limit", quota.limit());
        result.put("used", used);
        result.put("remaining", Math.max(0, quota.limit() - used));
        return result;
    }

    private int used(String userId, Quota quota, Context context) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(used_count), 0) FROM ai_feature_usage
                WHERE user_id = ? AND feature_code = ? AND period_key = ?
                  AND benefit_source = ?
                """, Integer.class, userId, quota.bucket(), periodKey(quota, context), source(context.tier()));
        return value == null ? 0 : value;
    }

    private String periodKey(Quota quota, Context context) {
        LocalDate start = context.trialStartsAt() == null
                ? LocalDate.now(CHINA_ZONE)
                : context.trialStartsAt().toLocalDate();
        return AiEntitlementPolicy.periodKey(quota, LocalDate.now(CHINA_ZONE), start);
    }

    private Context context(String userId) {
        ensureTrial(userId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT starts_at, expires_at FROM ai_growth_trial WHERE user_id = ?
                """, userId);
        LocalDateTime startsAt = rows.isEmpty() ? null : (LocalDateTime) rows.get(0).get("starts_at");
        LocalDateTime expiresAt = rows.isEmpty() ? null : (LocalDateTime) rows.get(0).get("expires_at");
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        boolean trialActive = startsAt != null && expiresAt != null
                && !startsAt.isAfter(now) && expiresAt.isAfter(now);
        Tier tier = paymentService.isVipActive(userId) ? Tier.VIP : trialActive ? Tier.TRIAL : Tier.FREE;
        return new Context(tier, trialActive, startsAt, expiresAt);
    }

    private void ensureTrial(String userId) {
        jdbc.update("""
                INSERT IGNORE INTO ai_growth_trial (user_id, starts_at, expires_at)
                SELECT user_id, created_at, DATE_ADD(created_at, INTERVAL 14 DAY)
                FROM user_account WHERE user_id = ? AND deleted_at IS NULL
                """, userId);
    }

    private String source(Tier tier) { return tier.name().toLowerCase(); }

    private record Context(
            Tier tier,
            boolean trialActive,
            LocalDateTime trialStartsAt,
            LocalDateTime trialExpiresAt) {}
}
