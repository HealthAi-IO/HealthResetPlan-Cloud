package io.healthresetplan.modules.admin;

import io.healthresetplan.common.result.R;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;
    private final OneApiService oneApiService;

    public AdminController(JdbcTemplate jdbc, OneApiService oneApiService) {
        this.jdbc = jdbc;
        this.oneApiService = oneApiService;
    }

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long healthIndicators = combinedSyncedCount("health_indicator");
        long reports = combinedSyncedCount("health_report");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", mapOf(
                "totalUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL"),
                "todayNewUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL AND created_at >= ?", today),
                "activeMembers", count("SELECT COUNT(DISTINCT user_id) FROM user_subscription WHERE status = 'active' AND expires_at > NOW(3)"),
                "cloudSyncUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL AND has_cloud_sync = 1"),
                "healthIndicators", healthIndicators,
                "reports", reports,
                "paidOrders", count("SELECT COUNT(*) FROM payment_order WHERE status = 'paid'"),
                "revenueYuan", moneyYuan(sumFen("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_order WHERE status = 'paid'"))
        ));
        data.put("trend", sevenDayTrend());
        data.put("indicatorTypes", indicatorTypes(null));
        data.put("recentUsers", recentUsers(8));
        data.put("recentOrders", recentOrders(8));
        return R.ok(data);
    }

    @GetMapping("/users")
    public R<Map<String, Object>> users(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        String where = " WHERE ua.deleted_at IS NULL ";
        Object[] countArgs = new Object[]{};
        Object[] listArgs;
        if (like != null) {
            where += " AND (ua.nickname LIKE ? OR ua.phone_tail LIKE ?) ";
            countArgs = new Object[]{like, like};
            listArgs = new Object[]{like, like, safeSize, offset};
        } else {
            listArgs = new Object[]{safeSize, offset};
        }

        Long total = queryLong("SELECT COUNT(*) FROM user_account ua" + where, countArgs);
        // Page first, then aggregate only the current page's users. This keeps admin
        // user list latency stable when health/sync rows grow with user volume.
        String sql = """
                WITH page_users AS (
                  SELECT
                    ua.user_id,
                    ua.custom_id,
                    ua.phone_tail,
                    ua.nickname,
                    ua.avatar_url,
                    ua.status,
                    ua.role_code,
                    ua.has_cloud_sync,
                    ua.created_at,
                    ua.updated_at
                  FROM user_account ua
                """ + where + """
                  ORDER BY ua.created_at DESC
                  LIMIT ? OFFSET ?
                ),
                login_stats AS (
                  SELECT us.user_id, MAX(us.created_at) AS last_login_at
                  FROM user_session us
                  JOIN page_users pu ON pu.user_id = us.user_id
                  GROUP BY us.user_id
                ),
                indicator_stats AS (
                  SELECT agg_source.user_id, SUM(agg_source.row_count) AS indicator_count
                  FROM (
                    SELECT hi.user_id, COUNT(*) AS row_count
                    FROM health_indicator hi
                    JOIN page_users pu ON pu.user_id = hi.user_id
                    WHERE hi.deleted_at IS NULL
                    GROUP BY hi.user_id
                    UNION ALL
                    SELECT sr.user_id, COUNT(*) AS row_count
                    FROM sync_record sr
                    JOIN page_users pu ON pu.user_id = sr.user_id
                    WHERE sr.table_name = 'health_indicator' AND sr.deleted_at IS NULL
                    GROUP BY sr.user_id
                  ) agg_source
                  GROUP BY agg_source.user_id
                ),
                report_stats AS (
                  SELECT agg_source.user_id, SUM(agg_source.row_count) AS report_count
                  FROM (
                    SELECT hr.user_id, COUNT(*) AS row_count
                    FROM health_report hr
                    JOIN page_users pu ON pu.user_id = hr.user_id
                    WHERE hr.deleted_at IS NULL
                    GROUP BY hr.user_id
                    UNION ALL
                    SELECT sr.user_id, COUNT(*) AS row_count
                    FROM sync_record sr
                    JOIN page_users pu ON pu.user_id = sr.user_id
                    WHERE sr.table_name = 'health_report' AND sr.deleted_at IS NULL
                    GROUP BY sr.user_id
                  ) agg_source
                  GROUP BY agg_source.user_id
                ),
                subscription_stats AS (
                  SELECT
                    sub.user_id,
                    COUNT(*) AS active_subscription_count,
                    MAX(sub.expires_at) AS member_expires_at
                  FROM user_subscription sub
                  JOIN page_users pu ON pu.user_id = sub.user_id
                  WHERE sub.status = 'active' AND sub.expires_at > NOW(3)
                  GROUP BY sub.user_id
                )
                SELECT
                  pu.user_id,
                  pu.custom_id,
                  pu.phone_tail,
                  pu.nickname,
                  pu.avatar_url,
                  pu.status,
                  pu.role_code,
                  pu.has_cloud_sync,
                  pu.created_at,
                  pu.updated_at,
                  ls.last_login_at,
                  COALESCE(ins.indicator_count, 0) AS indicator_count,
                  COALESCE(rs.report_count, 0) AS report_count,
                  COALESCE(ss.active_subscription_count, 0) AS active_subscription_count,
                  ss.member_expires_at
                FROM page_users pu
                LEFT JOIN login_stats ls ON ls.user_id = pu.user_id
                LEFT JOIN indicator_stats ins ON ins.user_id = pu.user_id
                LEFT JOIN report_stats rs ON rs.user_id = pu.user_id
                LEFT JOIN subscription_stats ss ON ss.user_id = pu.user_id
                ORDER BY pu.created_at DESC
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, listArgs).stream()
                .map(this::userRow)
                .toList();
        return R.ok(Map.of(
                "items", rows,
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @GetMapping("/users/{userId}")
    public R<Map<String, Object>> userDetail(@PathVariable String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  ua.user_id,
                  ua.custom_id,
                  ua.phone_tail,
                  ua.nickname,
                  ua.avatar_url,
                  ua.status,
                  ua.role_code,
                  ua.has_cloud_sync,
                  ua.created_at,
                  ua.updated_at,
                  (SELECT MAX(us.created_at) FROM user_session us WHERE us.user_id = ua.user_id) AS last_login_at,
                  ((SELECT COUNT(*) FROM health_indicator hi WHERE hi.user_id = ua.user_id AND hi.deleted_at IS NULL)
                   + (SELECT COUNT(*) FROM sync_record sr WHERE sr.user_id = ua.user_id AND sr.table_name = 'health_indicator' AND sr.deleted_at IS NULL)) AS indicator_count,
                  ((SELECT COUNT(*) FROM health_report hr WHERE hr.user_id = ua.user_id AND hr.deleted_at IS NULL)
                   + (SELECT COUNT(*) FROM sync_record sr WHERE sr.user_id = ua.user_id AND sr.table_name = 'health_report' AND sr.deleted_at IS NULL)) AS report_count,
                  (SELECT COUNT(*) FROM user_subscription sub WHERE sub.user_id = ua.user_id AND sub.status = 'active' AND sub.expires_at > NOW(3)) AS active_subscription_count,
                  (SELECT MAX(sub.expires_at) FROM user_subscription sub WHERE sub.user_id = ua.user_id AND sub.status = 'active') AS member_expires_at
                FROM user_account ua
                WHERE ua.deleted_at IS NULL AND ua.user_id = ?
                """, userId);
        if (rows.isEmpty()) {
            return R.fail(40401, "用户不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("profile", userRow(rows.get(0)));
        detail.put("indicatorTypes", indicatorTypes(userId));
        detail.put("subscriptions", jdbc.queryForList("""
                SELECT plan_code, status, starts_at, expires_at, payment_channel, payment_order_no, created_at
                FROM user_subscription
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, userId));
        detail.put("sessions", jdbc.queryForList("""
                SELECT device_id, ip, user_agent, expires_at, created_at
                FROM user_session
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, userId));
        return R.ok(detail);
    }

    @GetMapping("/vip/summary")
    public R<Map<String, Object>> vipSummary() {
        LocalDate start = LocalDate.now().minusDays(6);
        long activeMembers = count("SELECT COUNT(DISTINCT user_id) FROM user_subscription WHERE status = 'active' AND expires_at > NOW(3)");
        long totalUsers = count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL");
        long paidUsers = count("SELECT COUNT(DISTINCT user_id) FROM payment_order WHERE status = 'paid'");
        long sevenDayNewMembers = count("""
                SELECT COUNT(DISTINCT user_id)
                FROM user_subscription
                WHERE created_at >= ?
                """, start.atStartOfDay());
        long sevenDayRevenueFen = sumFen("""
                SELECT COALESCE(SUM(amount_fen), 0)
                FROM payment_order
                WHERE status = 'paid'
                  AND created_at >= ?
                """, start.atStartOfDay());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", mapOf(
                "activeMembers", activeMembers,
                "expiringSoon", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM user_subscription
                        WHERE status = 'active'
                          AND expires_at > NOW(3)
                          AND expires_at <= DATE_ADD(NOW(3), INTERVAL 7 DAY)
                        """),
                "expiredMembers", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM user_subscription
                        WHERE expires_at <= NOW(3)
                        """),
                "freeUsers", Math.max(0, totalUsers - activeMembers),
                "monthlyMembers", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM user_subscription
                        WHERE status = 'active'
                          AND expires_at > NOW(3)
                          AND plan_code = 'monthly'
                        """),
                "yearlyMembers", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM user_subscription
                        WHERE status = 'active'
                          AND expires_at > NOW(3)
                          AND plan_code = 'yearly'
                        """),
                "totalRevenueYuan", moneyYuan(sumFen("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_order WHERE status = 'paid'")),
                "todayRevenueYuan", moneyYuan(sumFen("""
                        SELECT COALESCE(SUM(amount_fen), 0)
                        FROM payment_order
                        WHERE status = 'paid'
                          AND created_at >= ?
                        """, LocalDate.now().atStartOfDay())),
                "paidUsers", paidUsers,
                "memberConversionRate", percent(activeMembers, totalUsers),
                "paidConversionRate", percent(paidUsers, totalUsers),
                "sevenDayNewMembers", sevenDayNewMembers,
                "sevenDayRevenueYuan", moneyYuan(sevenDayRevenueFen)
        ));
        data.put("dailyRevenue", revenueTrend(start));
        data.put("memberTrend", memberTrend(start));
        data.put("planBreakdown", jdbc.queryForList("""
                SELECT
                  mp.code AS planCode,
                  mp.name AS planName,
                  COUNT(DISTINCT CASE
                    WHEN us.status = 'active' AND us.expires_at > NOW(3) THEN us.user_id
                    ELSE NULL
                  END) AS activeMembers,
                  COUNT(DISTINCT us.user_id) AS totalMembers,
                  COALESCE(SUM(CASE
                    WHEN po.status = 'paid' THEN po.amount_fen
                    ELSE 0
                  END), 0) AS revenueFen
                FROM membership_plan mp
                LEFT JOIN user_subscription us ON us.plan_code = mp.code
                LEFT JOIN payment_order po ON po.plan_code = mp.code
                GROUP BY mp.code, mp.name, mp.sort_order
                ORDER BY mp.sort_order ASC, mp.code ASC
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("activeMembers", number(row.get("activeMembers")));
            out.put("totalMembers", number(row.get("totalMembers")));
            out.put("revenueYuan", moneyYuan(number(row.get("revenueFen"))));
            return out;
        }).toList());
        return R.ok(data);
    }

    @GetMapping("/vip/users")
    public R<Map<String, Object>> vipUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "membershipStatus", required = false) String membershipStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String status = membershipStatus == null ? "" : membershipStatus.trim().toLowerCase();

        StringBuilder where = new StringBuilder(" WHERE ua.deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        if (like != null) {
            where.append(" AND (ua.nickname LIKE ? OR ua.phone_tail LIKE ? OR ua.custom_id LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
        }

        switch (status) {
            case "active" -> where.append("""
                     AND EXISTS (
                       SELECT 1 FROM user_subscription sub
                       WHERE sub.user_id = ua.user_id
                         AND sub.status = 'active'
                         AND sub.expires_at > NOW(3)
                     )
                    """);
            case "expiring" -> where.append("""
                     AND EXISTS (
                       SELECT 1 FROM user_subscription sub
                       WHERE sub.user_id = ua.user_id
                         AND sub.status = 'active'
                         AND sub.expires_at > NOW(3)
                         AND sub.expires_at <= DATE_ADD(NOW(3), INTERVAL 7 DAY)
                     )
                    """);
            case "expired" -> where.append("""
                     AND NOT EXISTS (
                       SELECT 1 FROM user_subscription sub
                       WHERE sub.user_id = ua.user_id
                         AND sub.status = 'active'
                         AND sub.expires_at > NOW(3)
                     )
                     AND EXISTS (
                       SELECT 1 FROM user_subscription sub
                       WHERE sub.user_id = ua.user_id
                         AND sub.expires_at <= NOW(3)
                     )
                    """);
            case "free" -> where.append("""
                     AND NOT EXISTS (
                       SELECT 1 FROM user_subscription sub
                       WHERE sub.user_id = ua.user_id
                     )
                    """);
            default -> {
            }
        }

        Long total = queryLong("SELECT COUNT(*) FROM user_account ua" + where, params.toArray());

        String sql = """
                SELECT
                  ua.user_id,
                  ua.custom_id,
                  ua.phone_tail,
                  ua.nickname,
                  ua.avatar_url,
                  ua.status,
                  ua.role_code,
                  ua.has_cloud_sync,
                  ua.created_at,
                  (
                    SELECT sub.plan_code
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                    ORDER BY sub.expires_at DESC
                    LIMIT 1
                  ) AS latest_plan_code,
                  (
                    SELECT mp.name
                    FROM membership_plan mp
                    WHERE mp.code = (
                      SELECT sub.plan_code
                      FROM user_subscription sub
                      WHERE sub.user_id = ua.user_id
                      ORDER BY sub.expires_at DESC
                      LIMIT 1
                    )
                  ) AS latest_plan_name,
                  (
                    SELECT sub.status
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                    ORDER BY sub.expires_at DESC
                    LIMIT 1
                  ) AS latest_subscription_status,
                  (
                    SELECT sub.expires_at
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                    ORDER BY sub.expires_at DESC
                    LIMIT 1
                  ) AS member_expires_at,
                  (
                    SELECT sub.payment_channel
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                    ORDER BY sub.expires_at DESC
                    LIMIT 1
                  ) AS latest_payment_channel,
                  (
                    SELECT sub.payment_order_no
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                    ORDER BY sub.expires_at DESC
                    LIMIT 1
                  ) AS latest_payment_order_no,
                  (
                    SELECT COUNT(*)
                    FROM user_subscription sub
                    WHERE sub.user_id = ua.user_id
                      AND sub.status = 'active'
                      AND sub.expires_at > NOW(3)
                  ) AS active_subscription_count
                FROM user_account ua
                """ + where + """
                ORDER BY
                  CASE
                    WHEN (
                      SELECT COUNT(*)
                      FROM user_subscription sub
                      WHERE sub.user_id = ua.user_id
                        AND sub.status = 'active'
                        AND sub.expires_at > NOW(3)
                    ) > 0 THEN 0
                    ELSE 1
                  END,
                  member_expires_at ASC,
                  ua.created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, listArgs.toArray()).stream()
                .map(this::vipUserRow)
                .toList();
        return R.ok(Map.of(
                "items", rows,
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @GetMapping("/orders/summary")
    public R<Map<String, Object>> orderSummary() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        long paidOrders = count("SELECT COUNT(*) FROM payment_order WHERE status = 'paid'");
        long totalPaidFen = sumFen("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_order WHERE status = 'paid'");
        long totalOrders = count("SELECT COUNT(*) FROM payment_order");
        long pendingOrders = count("SELECT COUNT(*) FROM payment_order WHERE status = 'pending'");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", mapOf(
                "todayRevenueYuan", moneyYuan(sumFen("""
                        SELECT COALESCE(SUM(amount_fen), 0)
                        FROM payment_order
                        WHERE status = 'paid'
                          AND created_at >= ?
                        """, today.atStartOfDay())),
                "todayPaidOrders", count("""
                        SELECT COUNT(*)
                        FROM payment_order
                        WHERE status = 'paid'
                          AND created_at >= ?
                        """, today.atStartOfDay()),
                "todayPaidUsers", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM payment_order
                        WHERE status = 'paid'
                          AND created_at >= ?
                        """, today.atStartOfDay()),
                "totalRevenueYuan", moneyYuan(totalPaidFen),
                "refundAmountYuan", moneyYuan(sumFen("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_order WHERE status = 'refunded'")),
                "pendingOrders", pendingOrders,
                "paidOrders", paidOrders,
                "totalOrders", totalOrders,
                "averageOrderValueYuan", paidOrders > 0 ? moneyYuan(totalPaidFen / paidOrders) : "0.00",
                "paymentSuccessRate", percent(paidOrders, Math.max(totalOrders - pendingOrders, 0)),
                "pendingOrderRate", percent(pendingOrders, totalOrders),
                "sevenDayRevenueYuan", moneyYuan(sumFen("""
                        SELECT COALESCE(SUM(amount_fen), 0)
                        FROM payment_order
                        WHERE status = 'paid'
                          AND created_at >= ?
                        """, start.atStartOfDay()))
        ));
        data.put("dailyRevenue", revenueTrend(start));
        data.put("channelBreakdown", jdbc.queryForList("""
                SELECT
                  channel,
                  COUNT(*) AS paidOrders,
                  COALESCE(SUM(amount_fen), 0) AS revenueFen
                FROM payment_order
                WHERE status = 'paid'
                GROUP BY channel
                ORDER BY revenueFen DESC, paidOrders DESC
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("paidOrders", number(row.get("paidOrders")));
            out.put("revenueYuan", moneyYuan(number(row.get("revenueFen"))));
            return out;
        }).toList());
        data.put("planBreakdown", jdbc.queryForList("""
                SELECT
                  o.plan_code AS planCode,
                  COALESCE(mp.name, o.plan_code) AS planName,
                  COUNT(*) AS paidOrders,
                  COALESCE(SUM(o.amount_fen), 0) AS revenueFen
                FROM payment_order o
                LEFT JOIN membership_plan mp ON mp.code = o.plan_code
                WHERE o.status = 'paid'
                GROUP BY o.plan_code, mp.name
                ORDER BY revenueFen DESC, paidOrders DESC
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("paidOrders", number(row.get("paidOrders")));
            out.put("revenueYuan", moneyYuan(number(row.get("revenueFen"))));
            return out;
        }).toList());
        data.put("recentPaidOrders", recentOrders(8));
        return R.ok(data);
    }

    @GetMapping("/orders")
    public R<Map<String, Object>> orders(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "planCode", required = false) String planCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (like != null) {
            where.append(" AND (o.order_no LIKE ? OR ua.nickname LIKE ? OR ua.phone_tail LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status = ? ");
            params.add(status.trim());
        }
        if (channel != null && !channel.isBlank()) {
            where.append(" AND o.channel = ? ");
            params.add(channel.trim());
        }
        if (planCode != null && !planCode.isBlank()) {
            where.append(" AND o.plan_code = ? ");
            params.add(planCode.trim());
        }

        String countSql = """
                SELECT COUNT(*)
                FROM payment_order o
                LEFT JOIN user_account ua ON ua.user_id = o.user_id
                """ + where;
        Long total = queryLong(countSql, params.toArray());

        String sql = """
                SELECT
                  o.order_no,
                  o.user_id,
                  o.plan_code,
                  o.amount_fen,
                  o.channel,
                  o.channel_order_no,
                  o.status,
                  o.paid_at,
                  o.created_at,
                  ua.nickname,
                  ua.phone_tail,
                  mp.name AS plan_name
                FROM payment_order o
                LEFT JOIN user_account ua ON ua.user_id = o.user_id
                LEFT JOIN membership_plan mp ON mp.code = o.plan_code
                """ + where + """
                ORDER BY o.created_at DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, listArgs.toArray()).stream()
                .map(this::orderRow)
                .toList();
        return R.ok(Map.of(
                "items", rows,
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @GetMapping("/system/summary")
    public R<Map<String, Object>> systemSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "adminCount", count("SELECT COUNT(*) FROM admin_account WHERE deleted_at IS NULL"),
                "enabledAdminCount", count("SELECT COUNT(*) FROM admin_account WHERE deleted_at IS NULL AND status = 1"),
                "roleCount", count("SELECT COUNT(*) FROM admin_role"),
                "auditCount", count("SELECT COUNT(*) FROM audit_log")
        ));
        data.put("recentAudits", jdbc.queryForList("""
                SELECT id, actor_type, actor_id, action, target, ip, detail, created_at
                FROM audit_log
                ORDER BY created_at DESC, id DESC
                LIMIT 8
                """));
        return R.ok(data);
    }

    @GetMapping("/system/roles")
    public R<List<Map<String, Object>>> systemRoles() {
        return R.ok(jdbc.queryForList("""
                SELECT
                  r.code,
                  r.name,
                  r.permissions,
                  r.created_at,
                  r.updated_at,
                  (
                    SELECT COUNT(*)
                    FROM admin_account a
                    WHERE a.role_code = r.code
                      AND a.deleted_at IS NULL
                  ) AS adminCount
                FROM admin_role r
                ORDER BY FIELD(r.code, 'super_admin', 'operator', 'auditor'), r.code ASC
                """).stream().map(this::adminRoleRow).toList());
    }

    @GetMapping("/system/admins")
    public R<Map<String, Object>> systemAdmins(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "roleCode", required = false) String roleCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        StringBuilder where = new StringBuilder(" WHERE a.deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        if (like != null) {
            where.append(" AND (a.username LIKE ? OR a.nickname LIKE ?) ");
            params.add(like);
            params.add(like);
        }
        if (roleCode != null && !roleCode.isBlank()) {
            where.append(" AND a.role_code = ? ");
            params.add(normalizedText(roleCode));
        }

        Long total = queryLong("""
                SELECT COUNT(*)
                FROM admin_account a
                """ + where, params.toArray());

        String sql = """
                SELECT
                  a.id,
                  a.username,
                  a.nickname,
                  a.role_code,
                  a.status,
                  a.last_login_at,
                  a.last_login_ip,
                  a.created_at,
                  a.updated_at,
                  r.name AS role_name,
                  r.permissions
                FROM admin_account a
                LEFT JOIN admin_role r ON r.code = a.role_code
                """ + where + """
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);

        return R.ok(Map.of(
                "items", jdbc.queryForList(sql, listArgs.toArray()).stream().map(this::adminAccountRow).toList(),
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @PostMapping("/system/admins")
    public R<Map<String, Object>> createSystemAdmin(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        validateAdminPayload(payload, true);

        String username = normalizedText(payload.get("username"));
        if (count("SELECT COUNT(*) FROM admin_account WHERE username = ? AND deleted_at IS NULL", username) > 0) {
            throw new BusinessException(40901, "管理员账号已存在");
        }

        jdbc.update("""
                INSERT INTO admin_account (
                  username,
                  password_hash,
                  nickname,
                  role_code,
                  status,
                  last_login_ip
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                username,
                BCRYPT.encode(normalizedText(payload.get("password"))),
                fallbackText(payload.get("nickname"), username),
                normalizedText(payload.get("roleCode")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                ""
        );

        Long adminId = queryLong("SELECT LAST_INSERT_ID()");
        Map<String, Object> admin = getAdminById(adminId != null ? adminId : 0L);
        writeAuditLog(
                request,
                "system_admin_created",
                "admin_account:" + number(admin.get("id")),
                adminAuditDetail("created", null, admin)
        );
        return R.ok(admin);
    }

    @PutMapping("/system/admins/{adminId}")
    public R<Map<String, Object>> updateSystemAdmin(
            @PathVariable long adminId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> before = getAdminById(adminId);
        if (before.isEmpty()) {
            return R.fail(40401, "管理员不存在");
        }

        validateAdminPayload(payload, false);

        String password = normalizedText(payload.get("password"));
        if (password.isBlank()) {
            jdbc.update("""
                    UPDATE admin_account
                    SET nickname = ?,
                        role_code = ?,
                        status = ?
                    WHERE id = ? AND deleted_at IS NULL
                    """,
                    fallbackText(payload.get("nickname"), stringValue(before.get("username"))),
                    normalizedText(payload.get("roleCode")),
                    booleanValue(payload.get("enabled")) ? 1 : 0,
                    adminId
            );
        } else {
            jdbc.update("""
                    UPDATE admin_account
                    SET password_hash = ?,
                        nickname = ?,
                        role_code = ?,
                        status = ?
                    WHERE id = ? AND deleted_at IS NULL
                    """,
                    BCRYPT.encode(password),
                    fallbackText(payload.get("nickname"), stringValue(before.get("username"))),
                    normalizedText(payload.get("roleCode")),
                    booleanValue(payload.get("enabled")) ? 1 : 0,
                    adminId
            );
        }

        Map<String, Object> after = getAdminById(adminId);
        writeAuditLog(
                request,
                "system_admin_updated",
                "admin_account:" + adminId,
                adminAuditDetail("updated", before, after)
        );
        return R.ok(after);
    }

    @GetMapping("/content/templates/summary")
    public R<Map<String, Object>> contentTemplateSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "templateCount", count("SELECT COUNT(*) FROM ai_prompt_template WHERE deleted_at IS NULL"),
                "enabledTemplates", count("SELECT COUNT(*) FROM ai_prompt_template WHERE deleted_at IS NULL AND status = 1"),
                "disabledTemplates", count("SELECT COUNT(*) FROM ai_prompt_template WHERE deleted_at IS NULL AND status = 0")
        ));
        data.put("recentTemplates", jdbc.queryForList("""
                SELECT id, code, name, description, version, status, created_at, updated_at
                FROM ai_prompt_template
                WHERE deleted_at IS NULL
                ORDER BY updated_at DESC, id DESC
                LIMIT 8
                """).stream().map(this::templateRow).toList());
        return R.ok(data);
    }

    @GetMapping("/content/templates")
    public R<Map<String, Object>> contentTemplates(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        if (like != null) {
            where.append(" AND (code LIKE ? OR name LIKE ? OR description LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            params.add(booleanValue(status) ? 1 : 0);
        }

        Long total = queryLong("SELECT COUNT(*) FROM ai_prompt_template " + where, params.toArray());
        String sql = """
                SELECT id, code, name, content, description, version, status, created_at, updated_at
                FROM ai_prompt_template
                """ + where + """
                ORDER BY updated_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);

        return R.ok(Map.of(
                "items", jdbc.queryForList(sql, listArgs.toArray()).stream().map(this::templateRow).toList(),
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @PostMapping("/content/templates")
    public R<Map<String, Object>> createContentTemplate(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        validateTemplatePayload(payload, true);
        String code = normalizedText(payload.get("code"));
        if (count("SELECT COUNT(*) FROM ai_prompt_template WHERE code = ? AND deleted_at IS NULL", code) > 0) {
            throw new BusinessException(40901, "模板编码已存在");
        }

        jdbc.update("""
                INSERT INTO ai_prompt_template (
                  code,
                  name,
                  content,
                  description,
                  status,
                  version
                ) VALUES (?, ?, ?, ?, ?, 0)
                """,
                code,
                normalizedText(payload.get("name")),
                normalizedText(payload.get("content")),
                normalizedText(payload.get("description")),
                booleanValue(payload.get("enabled")) ? 1 : 0
        );

        Long templateId = queryLong("SELECT LAST_INSERT_ID()");
        Map<String, Object> template = getTemplateById(templateId != null ? templateId : 0L);
        writeAuditLog(request, "content_template_created", "ai_prompt_template:" + number(template.get("id")), templateAuditDetail("created", null, template));
        return R.ok(template);
    }

    @PutMapping("/content/templates/{templateId}")
    public R<Map<String, Object>> updateContentTemplate(
            @PathVariable long templateId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> before = getTemplateById(templateId);
        if (before.isEmpty()) {
            return R.fail(40401, "模板不存在");
        }

        validateTemplatePayload(payload, false);
        jdbc.update("""
                UPDATE ai_prompt_template
                SET name = ?,
                    content = ?,
                    description = ?,
                    status = ?,
                    version = version + 1
                WHERE id = ? AND deleted_at IS NULL
                """,
                normalizedText(payload.get("name")),
                normalizedText(payload.get("content")),
                normalizedText(payload.get("description")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                templateId
        );

        Map<String, Object> after = getTemplateById(templateId);
        writeAuditLog(request, "content_template_updated", "ai_prompt_template:" + templateId, templateAuditDetail("updated", before, after));
        return R.ok(after);
    }

    @GetMapping("/ai/providers/summary")
    public R<Map<String, Object>> aiProviderSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "providerCount", count("SELECT COUNT(*) FROM ai_provider_config WHERE deleted_at IS NULL"),
                "enabledProviders", count("SELECT COUNT(*) FROM ai_provider_config WHERE deleted_at IS NULL AND status = 1"),
                "totalWeight", count("SELECT COALESCE(SUM(weight), 0) FROM ai_provider_config WHERE deleted_at IS NULL"),
                "totalQpsLimit", count("SELECT COALESCE(SUM(qps_limit), 0) FROM ai_provider_config WHERE deleted_at IS NULL AND status = 1")
        ));
        data.put("providers", providerRows());
        return R.ok(data);
    }

    @GetMapping("/ai/providers")
    public R<List<Map<String, Object>>> aiProviders() {
        return R.ok(providerRows());
    }

    @PostMapping("/ai/providers")
    public R<Map<String, Object>> createAiProvider(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        validateProviderPayload(payload, true);
        String provider = normalizedText(payload.get("provider"));
        String model = normalizedText(payload.get("model"));
        if (count("SELECT COUNT(*) FROM ai_provider_config WHERE provider = ? AND model = ? AND deleted_at IS NULL", provider, model) > 0) {
            throw new BusinessException(40901, "厂商模型已存在");
        }

        jdbc.update("""
                INSERT INTO ai_provider_config (
                  provider,
                  base_url,
                  model,
                  api_key_cipher,
                  api_key_iv,
                  api_key_tag,
                  weight,
                  status,
                  qps_limit
                ) VALUES (?, ?, ?, ?, '', '', ?, ?, ?)
                """,
                provider,
                normalizedText(payload.get("baseUrl")),
                model,
                normalizedText(payload.get("apiKey")),
                intValue(payload.get("weight")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                intValue(payload.get("qpsLimit"))
        );

        Long providerId = queryLong("SELECT LAST_INSERT_ID()");
        Map<String, Object> providerRow = getProviderById(providerId != null ? providerId : 0L);
        oneApiService.reloadClients();
        writeAuditLog(request, "ai_provider_created", "ai_provider_config:" + number(providerRow.get("id")), providerAuditDetail("created", null, providerRow));
        return R.ok(providerRow);
    }

    @PutMapping("/ai/providers/{providerId}")
    public R<Map<String, Object>> updateAiProvider(
            @PathVariable long providerId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> before = getProviderById(providerId);
        if (before.isEmpty()) {
            return R.fail(40401, "AI 厂商配置不存在");
        }

        validateProviderPayload(payload, false);
        jdbc.update("""
                UPDATE ai_provider_config
                SET provider = ?,
                    base_url = ?,
                    model = ?,
                    weight = ?,
                    status = ?,
                    qps_limit = ?,
                    version = version + 1
                WHERE id = ? AND deleted_at IS NULL
                """,
                normalizedText(payload.get("provider")),
                normalizedText(payload.get("baseUrl")),
                normalizedText(payload.get("model")),
                intValue(payload.get("weight")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                intValue(payload.get("qpsLimit")),
                providerId
        );

        String apiKey = normalizedText(payload.get("apiKey"));
        if (!apiKey.isBlank()) {
            jdbc.update("""
                    UPDATE ai_provider_config
                    SET api_key_cipher = ?,
                        api_key_iv = '',
                        api_key_tag = ''
                    WHERE id = ? AND deleted_at IS NULL
                    """, apiKey, providerId);
        }

        Map<String, Object> after = getProviderById(providerId);
        oneApiService.reloadClients();
        writeAuditLog(request, "ai_provider_updated", "ai_provider_config:" + providerId, providerAuditDetail("updated", before, after));
        return R.ok(after);
    }

    @GetMapping("/reminders/summary")
    public R<Map<String, Object>> reminderSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "ruleCount", count("SELECT COUNT(*) FROM reminder_rule WHERE deleted_at IS NULL"),
                "enabledRules", count("SELECT COUNT(*) FROM reminder_rule WHERE deleted_at IS NULL AND status = 1"),
                "todayEvents", count("SELECT COUNT(*) FROM reminder_event WHERE created_at >= ?", LocalDate.now().atStartOfDay()),
                "pendingEvents", count("SELECT COUNT(*) FROM reminder_event WHERE status = 'pending'")
        ));
        data.put("typeBreakdown", jdbc.queryForList("""
                SELECT type, COUNT(*) AS ruleCount, COUNT(DISTINCT user_id) AS userCount
                FROM reminder_rule
                WHERE deleted_at IS NULL
                GROUP BY type
                ORDER BY ruleCount DESC
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("ruleCount", number(row.get("ruleCount")));
            out.put("userCount", number(row.get("userCount")));
            out.put("typeName", reminderTypeName(stringValue(row.get("type"))));
            return out;
        }).toList());
        data.put("recentRules", jdbc.queryForList("""
                SELECT id, user_id, type, cron_expr, channel, status, device_id, created_at, updated_at
                FROM reminder_rule
                WHERE deleted_at IS NULL
                ORDER BY updated_at DESC, id DESC
                LIMIT 20
                """).stream().map(this::reminderRuleRow).toList());
        return R.ok(data);
    }

    @GetMapping("/feedback/summary")
    public R<Map<String, Object>> feedbackSummary() {
        return R.ok(Map.of(
                "total", count("SELECT COUNT(*) FROM feedback"),
                "pending", count("SELECT COUNT(*) FROM feedback WHERE status = 0"),
                "processing", count("SELECT COUNT(*) FROM feedback WHERE status = 1"),
                "resolved", count("SELECT COUNT(*) FROM feedback WHERE status = 2"),
                "urgent", count("SELECT COUNT(*) FROM feedback WHERE priority = 'urgent' AND status < 2"),
                "todayNew", count("SELECT COUNT(*) FROM feedback WHERE created_at >= ?", LocalDate.now().atStartOfDay())
        ));
    }

    @GetMapping("/feedback")
    public R<Map<String, Object>> feedbackTickets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (f.content LIKE ? OR f.user_id LIKE ? OR ua.nickname LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND f.category = ? ");
            params.add(category.trim());
        }
        if (status != null) {
            where.append(" AND f.status = ? ");
            params.add(status);
        }
        if (priority != null && !priority.isBlank()) {
            where.append(" AND f.priority = ? ");
            params.add(priority.trim());
        }

        Long total = queryLong("SELECT COUNT(*) FROM feedback f LEFT JOIN user_account ua ON ua.user_id = f.user_id " + where,
                params.toArray());
        String sql = """
                SELECT f.id, f.user_id, f.category, f.content,
                       CASE
                         WHEN f.contact = '' THEN ''
                         WHEN CHAR_LENGTH(f.contact) <= 4 THEN '****'
                         ELSE CONCAT('****', RIGHT(f.contact, 4))
                       END AS masked_contact,
                       f.status, f.priority, f.assignee, f.resolution,
                       f.created_at, f.updated_at, ua.nickname
                FROM feedback f
                LEFT JOIN user_account ua ON ua.user_id = f.user_id
                """ + where + """
                ORDER BY FIELD(f.priority, 'urgent', 'high', 'normal', 'low'), f.created_at DESC, f.id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);
        return R.ok(Map.of(
                "items", jdbc.queryForList(sql, listArgs.toArray()),
                "total", total == null ? 0 : total,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @PutMapping("/feedback/{feedbackId}")
    public R<Map<String, Object>> updateFeedbackTicket(
            @PathVariable long feedbackId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        List<Map<String, Object>> beforeRows = jdbc.queryForList("SELECT * FROM feedback WHERE id = ?", feedbackId);
        if (beforeRows.isEmpty()) return R.fail(40401, "反馈工单不存在");

        int status = intValue(payload.get("status"));
        String priority = normalizedText(payload.get("priority"));
        if (status < 0 || status > 3) throw new BusinessException(40001, "工单状态不合法");
        if (!List.of("low", "normal", "high", "urgent").contains(priority)) {
            throw new BusinessException(40001, "工单优先级不合法");
        }

        jdbc.update("""
                UPDATE feedback
                SET status = ?, priority = ?, assignee = ?, resolution = ?
                WHERE id = ?
                """,
                status,
                priority,
                normalizedText(payload.get("assignee")),
                normalizedText(payload.get("resolution")),
                feedbackId);
        Map<String, Object> after = jdbc.queryForMap("SELECT * FROM feedback WHERE id = ?", feedbackId);
        writeAuditLog(request, "feedback_ticket_updated", "feedback:" + feedbackId,
                "before=" + beforeRows.get(0) + ";after=" + after);
        return R.ok(after);
    }

    @GetMapping("/platforms/summary")
    public R<Map<String, Object>> platformSummary() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "todayActiveDevices", count("""
                        SELECT COUNT(DISTINCT CONCAT(user_id, ':', device_id))
                        FROM user_session
                        WHERE created_at >= ?
                        """, today),
                "todayActiveUsers", count("""
                        SELECT COUNT(DISTINCT user_id)
                        FROM user_session
                        WHERE created_at >= ?
                        """, today),
                "sevenDayActiveDevices", count("""
                        SELECT COUNT(DISTINCT CONCAT(user_id, ':', device_id))
                        FROM user_session
                        WHERE created_at >= DATE_SUB(NOW(3), INTERVAL 7 DAY)
                        """),
                "releasePlatforms", count("""
                        SELECT COUNT(DISTINCT platform)
                        FROM app_release
                        WHERE deleted_at IS NULL AND status = 1
                        """)
        ));
        data.put("platformBreakdown", platformBreakdown());
        data.put("latestVersions", jdbc.queryForList("""
                SELECT
                  ar.platform,
                  ar.channel,
                  ar.version_name,
                  ar.version_code,
                  ar.release_stage,
                  ar.is_force_update,
                  ar.rollout_percent,
                  ar.released_at
                FROM app_release ar
                INNER JOIN (
                  SELECT platform, MAX(COALESCE(released_at, created_at)) AS latestReleasedAt
                  FROM app_release
                  WHERE deleted_at IS NULL
                  GROUP BY platform
                ) latest ON latest.platform = ar.platform
                        AND COALESCE(ar.released_at, ar.created_at) = latest.latestReleasedAt
                WHERE ar.deleted_at IS NULL
                ORDER BY FIELD(ar.platform, 'windows', 'macos', 'android', 'ios', 'wechat', 'web')
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("platformName", platformName(stringValue(row.get("platform"))));
            out.put("forceUpdate", number(row.get("is_force_update")) == 1);
            return out;
        }).toList());
        data.put("versionDistribution", jdbc.queryForList("""
                SELECT
                  platform,
                  app_version,
                  channel,
                  COUNT(*) AS sessionCount,
                  COUNT(DISTINCT user_id) AS activeUsers,
                  MAX(created_at) AS lastSeenAt
                FROM user_session
                WHERE app_version <> ''
                GROUP BY platform, app_version, channel
                ORDER BY FIELD(platform, 'windows', 'macos', 'android', 'ios', 'wechat', 'web'),
                         activeUsers DESC,
                         sessionCount DESC
                LIMIT 60
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("platformName", platformName(stringValue(row.get("platform"))));
            out.put("sessionCount", number(row.get("sessionCount")));
            out.put("activeUsers", number(row.get("activeUsers")));
            out.put("upgradeStatus", versionDistributionStatus(row));
            return out;
        }).toList());
        return R.ok(data);
    }

    @GetMapping("/platforms/sessions")
    public R<Map<String, Object>> platformSessions(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        String platformExpr = """
                CASE
                  WHEN LOWER(us.user_agent) LIKE '%windows%' THEN 'windows'
                  WHEN LOWER(us.user_agent) LIKE '%mac os%' OR LOWER(us.user_agent) LIKE '%macintosh%' THEN 'macos'
                  WHEN LOWER(us.user_agent) LIKE '%android%' THEN 'android'
                  WHEN LOWER(us.user_agent) LIKE '%iphone%' OR LOWER(us.user_agent) LIKE '%ipad%' OR LOWER(us.user_agent) LIKE '%ios%' THEN 'ios'
                  WHEN LOWER(us.user_agent) LIKE '%micromessenger%' THEN 'wechat'
                  ELSE 'web'
                END
                """;

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (platform != null && !platform.isBlank()) {
            where.append(" AND ").append(platformExpr).append(" = ? ");
            params.add(platform.trim().toLowerCase());
        }
        if (like != null) {
            where.append(" AND (ua.nickname LIKE ? OR ua.phone_tail LIKE ? OR us.device_id LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String countSql = """
                SELECT COUNT(*)
                FROM user_session us
                LEFT JOIN user_account ua ON ua.user_id = us.user_id
                """ + where;
        Long total = queryLong(countSql, params.toArray());

        String sql = """
                SELECT
                  us.user_id,
                  us.device_id,
                  us.ip,
                  us.user_agent,
                  us.expires_at,
                  us.created_at,
                  ua.nickname,
                  ua.phone_tail,
                  """ + platformExpr + """
                   AS platform
                FROM user_session us
                LEFT JOIN user_account ua ON ua.user_id = us.user_id
                """ + where + """
                ORDER BY us.created_at DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, listArgs.toArray()).stream()
                .map(this::platformSessionRow)
                .toList();
        return R.ok(Map.of(
                "items", rows,
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @GetMapping("/releases/summary")
    public R<Map<String, Object>> releaseSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "totalReleases", count("SELECT COUNT(*) FROM app_release WHERE deleted_at IS NULL"),
                "grayReleases", count("SELECT COUNT(*) FROM app_release WHERE deleted_at IS NULL AND release_stage = 'gray'"),
                "forceUpdateReleases", count("SELECT COUNT(*) FROM app_release WHERE deleted_at IS NULL AND is_force_update = 1"),
                "activePlatforms", count("SELECT COUNT(DISTINCT platform) FROM app_release WHERE deleted_at IS NULL AND status = 1")
        ));
        data.put("platformBreakdown", jdbc.queryForList("""
                SELECT
                  platform,
                  COUNT(*) AS releaseCount,
                  MAX(version_name) AS latestVersion,
                  MAX(COALESCE(released_at, created_at)) AS latestReleasedAt
                FROM app_release
                WHERE deleted_at IS NULL
                GROUP BY platform
                ORDER BY FIELD(platform, 'windows', 'macos', 'android', 'ios', 'wechat', 'web')
                """).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("releaseCount", number(row.get("releaseCount")));
            out.put("platformName", platformName(stringValue(row.get("platform"))));
            return out;
        }).toList());
        data.put("recentReleases", jdbc.queryForList("""
                SELECT
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  released_at,
                  created_at
                FROM app_release
                WHERE deleted_at IS NULL
                ORDER BY COALESCE(released_at, created_at) DESC
                LIMIT 8
                """).stream().map(this::releaseRow).toList());
        return R.ok(data);
    }

    @GetMapping("/releases")
    public R<Map<String, Object>> releases(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "releaseStage", required = false) String releaseStage,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        if (platform != null && !platform.isBlank()) {
            where.append(" AND platform = ? ");
            params.add(platform.trim().toLowerCase());
        }
        if (releaseStage != null && !releaseStage.isBlank()) {
            where.append(" AND release_stage = ? ");
            params.add(releaseStage.trim().toLowerCase());
        }
        if (channel != null && !channel.isBlank()) {
            where.append(" AND channel = ? ");
            params.add(channel.trim());
        }

        Long total = queryLong("SELECT COUNT(*) FROM app_release" + where, params.toArray());
        String sql = """
                SELECT
                  id,
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  status,
                  released_at,
                  created_at,
                  updated_at
                FROM app_release
                """ + where + """
                ORDER BY COALESCE(released_at, created_at) DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, listArgs.toArray()).stream()
                .map(this::releaseRow)
                .toList();
        return R.ok(Map.of(
                "items", rows,
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @GetMapping("/releases/audits")
    public R<Map<String, Object>> releaseAudits(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder("""
                WHERE actor_type = 'admin'
                  AND action LIKE 'release_%'
                """);
        List<Object> params = new ArrayList<>();
        if (platform != null && !platform.isBlank()) {
            where.append(" AND detail LIKE ? ");
            params.add("%platform=" + normalizedText(platform).toLowerCase() + "%");
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ? ");
            params.add(normalizedText(action));
        }

        Long total = queryLong("SELECT COUNT(*) FROM audit_log " + where, params.toArray());
        String sql = """
                SELECT
                  id,
                  actor_type,
                  actor_id,
                  action,
                  target,
                  ip,
                  user_agent,
                  detail,
                  created_at
                FROM audit_log
                """ + where + """
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(safeSize);
        listArgs.add(offset);

        return R.ok(Map.of(
                "items", jdbc.queryForList(sql, listArgs.toArray()),
                "total", total != null ? total : 0,
                "page", safePage,
                "pageSize", safeSize
        ));
    }

    @PostMapping("/releases")
    public R<Map<String, Object>> createRelease(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        validateReleasePayload(payload);

        jdbc.update("""
                INSERT INTO app_release (
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  status,
                  released_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                normalizedText(payload.get("platform")),
                normalizedText(payload.get("channel")),
                normalizedText(payload.get("versionName")),
                intValue(payload.get("versionCode")),
                normalizedText(payload.get("releaseStage")),
                booleanValue(payload.get("forceUpdate")) ? 1 : 0,
                intValue(payload.get("rolloutPercent")),
                normalizedText(payload.get("packageUrl")),
                decimalValue(payload.get("packageSizeMb")),
                normalizedText(payload.get("minSupportedVersion")),
                normalizedText(payload.get("releaseNotes")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                releaseTime(payload.get("releasedAt"))
        );

        Long releaseId = queryLong("SELECT LAST_INSERT_ID()");
        Map<String, Object> release = getReleaseById(releaseId != null ? releaseId : 0L);
        writeAuditLog(
                request,
                "release_created",
                releaseAuditTarget(release),
                releaseAuditDetail("created", release, null, release, normalizedText(payload.get("auditReason")))
        );
        return R.ok(release);
    }

    @PutMapping("/releases/{releaseId}")
    public R<Map<String, Object>> updateRelease(
            @PathVariable long releaseId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        if (count("SELECT COUNT(*) FROM app_release WHERE id = ? AND deleted_at IS NULL", releaseId) == 0) {
            return R.fail(40401, "版本记录不存在");
        }

        Map<String, Object> before = getReleaseById(releaseId);
        validateReleasePayload(payload);

        jdbc.update("""
                UPDATE app_release
                SET platform = ?,
                    channel = ?,
                    version_name = ?,
                    version_code = ?,
                    release_stage = ?,
                    is_force_update = ?,
                    rollout_percent = ?,
                    package_url = ?,
                    package_size_mb = ?,
                    min_supported_version = ?,
                    release_notes = ?,
                    status = ?,
                    released_at = ?
                WHERE id = ? AND deleted_at IS NULL
                """,
                normalizedText(payload.get("platform")),
                normalizedText(payload.get("channel")),
                normalizedText(payload.get("versionName")),
                intValue(payload.get("versionCode")),
                normalizedText(payload.get("releaseStage")),
                booleanValue(payload.get("forceUpdate")) ? 1 : 0,
                intValue(payload.get("rolloutPercent")),
                normalizedText(payload.get("packageUrl")),
                decimalValue(payload.get("packageSizeMb")),
                normalizedText(payload.get("minSupportedVersion")),
                normalizedText(payload.get("releaseNotes")),
                booleanValue(payload.get("enabled")) ? 1 : 0,
                releaseTime(payload.get("releasedAt")),
                releaseId
        );

        Map<String, Object> after = getReleaseById(releaseId);
        String auditAction = forceUpdateChanged(before, after)
                ? (booleanValue(after.get("forceUpdate")) ? "release_force_update_enabled" : "release_force_update_disabled")
                : "release_updated";
        writeAuditLog(
                request,
                auditAction,
                releaseAuditTarget(after),
                releaseAuditDetail("updated", after, before, after, normalizedText(payload.get("auditReason")))
        );
        return R.ok(after);
    }

    @PostMapping("/releases/{releaseId}/pause")
    public R<Map<String, Object>> pauseRelease(
            @PathVariable long releaseId,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> before = getReleaseById(releaseId);
        if (before.isEmpty()) {
            return R.fail(40401, "版本记录不存在");
        }

        jdbc.update("""
                UPDATE app_release
                SET release_stage = 'paused',
                    status = 0
                WHERE id = ? AND deleted_at IS NULL
                """, releaseId);

        Map<String, Object> after = getReleaseById(releaseId);
        writeAuditLog(
                request,
                "release_paused",
                releaseAuditTarget(after),
                releaseAuditDetail("paused", after, before, after, payload == null ? "" : normalizedText(payload.get("reason")))
        );
        return R.ok(after);
    }

    @PostMapping("/releases/{releaseId}/rollback")
    public R<Map<String, Object>> rollbackRelease(
            @PathVariable long releaseId,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> current = getReleaseById(releaseId);
        if (current.isEmpty()) {
            return R.fail(40401, "版本记录不存在");
        }

        Long targetReleaseId = payload == null ? null : nullableLong(payload.get("targetReleaseId"));
        Map<String, Object> target = findRollbackTarget(
                releaseId,
                stringValue(current.get("platform")),
                stringValue(current.get("channel")),
                targetReleaseId
        );
        if (target.isEmpty()) {
            throw new BusinessException(40001, "未找到可回滚的目标版本");
        }

        jdbc.update("""
                UPDATE app_release
                SET release_stage = 'paused',
                    status = 0
                WHERE id = ? AND deleted_at IS NULL
                """, releaseId);
        jdbc.update("""
                UPDATE app_release
                SET release_stage = 'release',
                    status = 1,
                    released_at = ?
                WHERE id = ? AND deleted_at IS NULL
                """, LocalDateTime.now(), number(target.get("id")));

        Map<String, Object> pausedCurrent = getReleaseById(releaseId);
        Map<String, Object> activatedTarget = getReleaseById(number(target.get("id")));
        writeAuditLog(
                request,
                "release_rolled_back",
                releaseAuditTarget(activatedTarget),
                rollbackAuditDetail(
                        current,
                        pausedCurrent,
                        activatedTarget,
                        payload == null ? "" : normalizedText(payload.get("reason"))
                )
        );

        return R.ok(Map.of(
                "pausedRelease", pausedCurrent,
                "rollbackTarget", activatedTarget
        ));
    }

    @GetMapping("/releases/check")
    public R<Map<String, Object>> checkRelease(
            @RequestParam("platform") String platform,
            @RequestParam("currentVersion") String currentVersion,
            @RequestParam(value = "channel", required = false) String channel) {
        String normalizedPlatform = normalizedText(platform).toLowerCase();
        String normalizedVersion = normalizedText(currentVersion);
        String normalizedChannel = normalizedText(channel);

        if (normalizedPlatform.isBlank() || normalizedVersion.isBlank()) {
            throw new BusinessException(40001, "platform 和 currentVersion 不能为空");
        }

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE deleted_at IS NULL
                  AND status = 1
                  AND platform = ?
                """);
        args.add(normalizedPlatform);
        if (!normalizedChannel.isBlank()) {
            where.append(" AND channel = ? ");
            args.add(normalizedChannel);
        }

        String sql = """
                SELECT
                  id,
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  status,
                  released_at,
                  created_at,
                  updated_at
                FROM app_release
                """ + where + """
                ORDER BY version_code DESC, COALESCE(released_at, created_at) DESC
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        if (rows.isEmpty()) {
            return R.ok(Map.of(
                    "hasUpdate", false,
                    "forceUpdate", false,
                    "reason", "no_release"
            ));
        }

        Map<String, Object> latest = releaseRow(rows.get(0));
        int latestVersionCode = intValue(latest.get("version_code"));
        int currentVersionCode = compareVersionCode(normalizedVersion);
        int minVersionCode = compareVersionCode(stringValue(latest.get("min_supported_version")));
        boolean hasUpdate = latestVersionCode > currentVersionCode;
        boolean forceUpdate = booleanValue(latest.get("forceUpdate")) || currentVersionCode < minVersionCode;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hasUpdate", hasUpdate);
        data.put("forceUpdate", hasUpdate && forceUpdate);
        data.put("currentVersion", normalizedVersion);
        data.put("latestRelease", latest);
        data.put("reason", hasUpdate ? "new_version_available" : "latest");
        return R.ok(data);
    }

    private List<Map<String, Object>> sevenDayTrend() {
        LocalDate start = LocalDate.now().minusDays(6);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.day,
                       COALESCE(u.users, 0) AS users,
                       COALESCE(h.indicators, 0) AS indicators,
                       COALESCE(o.orders, 0) AS orders
                FROM (
                  SELECT CURDATE() - INTERVAL 6 DAY AS day UNION ALL
                  SELECT CURDATE() - INTERVAL 5 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 4 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 3 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 2 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 1 DAY UNION ALL
                  SELECT CURDATE()
                ) d
                LEFT JOIN (
                  SELECT DATE(created_at) AS day, COUNT(*) AS users
                  FROM user_account
                  WHERE deleted_at IS NULL AND created_at >= ?
                  GROUP BY DATE(created_at)
                ) u ON u.day = d.day
                LEFT JOIN (
                  SELECT day, SUM(indicators) AS indicators
                  FROM (
                    SELECT DATE(created_at) AS day, COUNT(*) AS indicators
                    FROM health_indicator
                    WHERE deleted_at IS NULL AND created_at >= ?
                    GROUP BY DATE(created_at)
                    UNION ALL
                    SELECT DATE(created_at) AS day, COUNT(*) AS indicators
                    FROM sync_record
                    WHERE table_name = 'health_indicator'
                      AND deleted_at IS NULL
                      AND created_at >= ?
                    GROUP BY DATE(created_at)
                  ) hAll
                  GROUP BY day
                ) h ON h.day = d.day
                LEFT JOIN (
                  SELECT DATE(created_at) AS day, COUNT(*) AS orders
                  FROM payment_order
                  WHERE status = 'paid' AND created_at >= ?
                  GROUP BY DATE(created_at)
                ) o ON o.day = d.day
                ORDER BY d.day ASC
                """, start, start, start, start);
        return rows.stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("day", String.valueOf(row.get("day")));
            out.put("users", number(row.get("users")));
            out.put("indicators", number(row.get("indicators")));
            out.put("orders", number(row.get("orders")));
            return out;
        }).toList();
    }

    private List<Map<String, Object>> revenueTrend(LocalDate start) {
        return jdbc.queryForList("""
                SELECT d.day,
                       COALESCE(o.paid_orders, 0) AS paid_orders,
                       COALESCE(o.revenue_fen, 0) AS revenue_fen
                FROM (
                  SELECT CURDATE() - INTERVAL 6 DAY AS day UNION ALL
                  SELECT CURDATE() - INTERVAL 5 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 4 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 3 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 2 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 1 DAY UNION ALL
                  SELECT CURDATE()
                ) d
                LEFT JOIN (
                  SELECT DATE(created_at) AS day,
                         COUNT(*) AS paid_orders,
                         COALESCE(SUM(amount_fen), 0) AS revenue_fen
                  FROM payment_order
                  WHERE status = 'paid'
                    AND created_at >= ?
                  GROUP BY DATE(created_at)
                ) o ON o.day = d.day
                ORDER BY d.day ASC
                """, start).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("day", String.valueOf(row.get("day")));
            out.put("paidOrders", number(row.get("paid_orders")));
            out.put("revenueYuan", moneyYuan(number(row.get("revenue_fen"))));
            return out;
        }).toList();
    }

    private List<Map<String, Object>> memberTrend(LocalDate start) {
        return jdbc.queryForList("""
                SELECT d.day,
                       COALESCE(s.new_members, 0) AS new_members,
                       COALESCE(s.active_members, 0) AS active_members
                FROM (
                  SELECT CURDATE() - INTERVAL 6 DAY AS day UNION ALL
                  SELECT CURDATE() - INTERVAL 5 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 4 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 3 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 2 DAY UNION ALL
                  SELECT CURDATE() - INTERVAL 1 DAY UNION ALL
                  SELECT CURDATE()
                ) d
                LEFT JOIN (
                  SELECT DATE(created_at) AS day,
                         COUNT(DISTINCT user_id) AS new_members,
                         COUNT(DISTINCT CASE
                           WHEN status = 'active' AND expires_at > NOW(3) THEN user_id
                           ELSE NULL
                         END) AS active_members
                  FROM user_subscription
                  WHERE created_at >= ?
                  GROUP BY DATE(created_at)
                ) s ON s.day = d.day
                ORDER BY d.day ASC
                """, start).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("day", String.valueOf(row.get("day")));
            out.put("newMembers", number(row.get("new_members")));
            out.put("activeMembers", number(row.get("active_members")));
            return out;
        }).toList();
    }

    private List<Map<String, Object>> indicatorTypes(String userId) {
        if (userId == null || userId.isBlank()) {
            return jdbc.queryForList("""
                    SELECT type, SUM(count) AS count
                    FROM (
                      SELECT type, COUNT(*) AS count
                      FROM health_indicator
                      WHERE deleted_at IS NULL
                      GROUP BY type
                      UNION ALL
                      SELECT COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.type')), ''), 'unknown') AS type,
                             COUNT(*) AS count
                      FROM sync_record
                      WHERE table_name = 'health_indicator'
                        AND deleted_at IS NULL
                      GROUP BY type
                    ) t
                    GROUP BY type
                    ORDER BY count DESC
                    LIMIT 12
                    """);
        }
        return jdbc.queryForList("""
                SELECT type, SUM(count) AS count
                FROM (
                  SELECT type, COUNT(*) AS count
                  FROM health_indicator
                  WHERE deleted_at IS NULL AND user_id = ?
                  GROUP BY type
                  UNION ALL
                  SELECT COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.type')), ''), 'unknown') AS type,
                         COUNT(*) AS count
                  FROM sync_record
                  WHERE table_name = 'health_indicator'
                    AND deleted_at IS NULL
                    AND user_id = ?
                  GROUP BY type
                ) t
                GROUP BY type
                ORDER BY count DESC
                LIMIT 12
                """, userId, userId);
    }

    private long combinedSyncedCount(String tableName) {
        if (!"health_indicator".equals(tableName) && !"health_report".equals(tableName)) {
            return 0;
        }
        long legacy = count("SELECT COUNT(*) FROM " + tableName + " WHERE deleted_at IS NULL");
        long generic = count(
                "SELECT COUNT(*) FROM sync_record WHERE table_name = ? AND deleted_at IS NULL",
                tableName
        );
        return legacy + generic;
    }

    private List<Map<String, Object>> recentUsers(int limit) {
        return jdbc.queryForList("""
                SELECT user_id, custom_id, phone_tail, nickname, status, role_code, has_cloud_sync, created_at
                FROM user_account
                WHERE deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT ?
                """, limit).stream().map(this::userRow).toList();
    }

    private List<Map<String, Object>> recentOrders(int limit) {
        return jdbc.queryForList("""
                SELECT order_no, user_id, plan_code, amount_fen, channel, status, paid_at, created_at
                FROM payment_order
                ORDER BY created_at DESC
                LIMIT ?
                """, limit).stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("amountYuan", moneyYuan(number(row.get("amount_fen"))));
            return out;
        }).toList();
    }

    private Map<String, Object> vipUserRow(Map<String, Object> row) {
        Map<String, Object> out = userRow(row);
        String latestPlanCode = stringValue(row.get("latest_plan_code"));
        String latestPlanName = stringValue(row.get("latest_plan_name"));
        long activeSubscriptions = number(row.get("active_subscription_count"));
        String latestStatus = stringValue(row.get("latest_subscription_status"));
        String membershipStatus = "free";
        String membershipStatusText = "免费用户";

        if (activeSubscriptions > 0) {
            membershipStatus = "active";
            membershipStatusText = "活跃会员";
            if (isWithinDays(row.get("member_expires_at"), 7)) {
                membershipStatus = "expiring";
                membershipStatusText = "即将到期";
            }
        } else if (!latestStatus.isBlank() || row.get("member_expires_at") != null) {
            membershipStatus = "expired";
            membershipStatusText = "已过期";
        }

        out.put("latestPlanCode", latestPlanCode);
        out.put("latestPlanName", latestPlanName.isBlank() ? latestPlanCode : latestPlanName);
        out.put("membershipStatus", membershipStatus);
        out.put("membershipStatusText", membershipStatusText);
        out.put("latestPaymentChannel", stringValue(row.get("latest_payment_channel")));
        out.put("latestPaymentOrderNo", stringValue(row.get("latest_payment_order_no")));
        return out;
    }

    private Map<String, Object> orderRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        String maskedTail = maskPhoneTail(stringValue(row.get("phone_tail")));
        out.put("account", accountDisplay(maskedTail, stringValue(row.get("nickname"))));
        out.put("amountYuan", moneyYuan(number(row.get("amount_fen"))));
        out.put("planName", stringValue(row.get("plan_name")).isBlank() ? stringValue(row.get("plan_code")) : stringValue(row.get("plan_name")));
        return out;
    }

    private Map<String, Object> platformSessionRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        String maskedTail = maskPhoneTail(stringValue(row.get("phone_tail")));
        out.put("account", accountDisplay(maskedTail, stringValue(row.get("nickname"))));
        out.put("platformName", platformName(stringValue(row.get("platform"))));
        return out;
    }

    private Map<String, Object> adminRoleRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        String permissions = stringValue(row.get("permissions"));
        out.put("adminCount", number(row.get("adminCount")));
        out.put("permissionList", "*".equals(permissions) ? List.of("*") : List.of(permissions.split(",")));
        return out;
    }

    private Map<String, Object> adminAccountRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("enabled", number(row.get("status")) == 1);
        out.put("statusText", number(row.get("status")) == 1 ? "启用中" : "已停用");
        out.put("roleName", stringValue(row.get("role_name")).isBlank() ? stringValue(row.get("role_code")) : stringValue(row.get("role_name")));
        String permissions = stringValue(row.get("permissions"));
        out.put("permissionList", "*".equals(permissions) ? List.of("*") : List.of(permissions.split(",")));
        return out;
    }

    private Map<String, Object> templateRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("enabled", number(row.get("status")) == 1);
        out.put("statusText", number(row.get("status")) == 1 ? "启用中" : "已停用");
        out.put("contentLength", stringValue(row.get("content")).length());
        return out;
    }

    private Map<String, Object> providerRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("baseUrl", stringValue(row.get("base_url")));
        out.put("qpsLimit", number(row.get("qps_limit")));
        out.put("enabled", number(row.get("status")) == 1);
        out.put("statusText", number(row.get("status")) == 1 ? "启用中" : "已停用");
        out.put("hasApiKey", !stringValue(row.get("api_key_cipher")).isBlank());
        out.remove("api_key_cipher");
        out.remove("api_key_iv");
        out.remove("api_key_tag");
        return out;
    }

    private Map<String, Object> reminderRuleRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("typeName", reminderTypeName(stringValue(row.get("type"))));
        out.put("enabled", number(row.get("status")) == 1);
        out.put("statusText", number(row.get("status")) == 1 ? "启用中" : "已停用");
        return out;
    }

    private Map<String, Object> releaseRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("platformName", platformName(stringValue(row.get("platform"))));
        out.put("forceUpdate", number(row.get("is_force_update")) == 1);
        out.put("enabled", number(row.get("status")) == 1);
        return out;
    }

    private Map<String, Object> findRollbackTarget(long currentReleaseId, String platform, String channel, Long targetReleaseId) {
        if (targetReleaseId != null && targetReleaseId > 0) {
            return getReleaseById(targetReleaseId);
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  id,
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  status,
                  released_at,
                  created_at,
                  updated_at
                FROM app_release
                WHERE deleted_at IS NULL
                  AND id <> ?
                  AND platform = ?
                  AND channel = ?
                ORDER BY version_code DESC, COALESCE(released_at, created_at) DESC
                LIMIT 1
                """, currentReleaseId, platform, channel);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return releaseRow(rows.get(0));
    }

    private String versionDistributionStatus(Map<String, Object> row) {
        String platform = stringValue(row.get("platform"));
        String version = stringValue(row.get("app_version"));
        List<Map<String, Object>> releases = jdbc.queryForList("""
                SELECT version_code, min_supported_version, is_force_update
                FROM app_release
                WHERE deleted_at IS NULL
                  AND status = 1
                  AND platform = ?
                ORDER BY version_code DESC
                LIMIT 1
                """, platform);
        if (releases.isEmpty()) {
            return "unknown";
        }

        Map<String, Object> latest = releases.get(0);
        int currentCode = compareVersionCode(version);
        int latestCode = intValue(latest.get("version_code"));
        int minCode = compareVersionCode(stringValue(latest.get("min_supported_version")));
        boolean force = number(latest.get("is_force_update")) == 1;

        if (currentCode < minCode) {
            return "must_upgrade";
        }
        if (force && currentCode < latestCode) {
            return "force_upgrade";
        }
        if (currentCode < latestCode) {
            return "optional_upgrade";
        }
        return "latest";
    }

    private Map<String, Object> getReleaseById(long releaseId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  id,
                  platform,
                  channel,
                  version_name,
                  version_code,
                  release_stage,
                  is_force_update,
                  rollout_percent,
                  package_url,
                  package_size_mb,
                  min_supported_version,
                  release_notes,
                  status,
                  released_at,
                  created_at,
                  updated_at
                FROM app_release
                WHERE id = ? AND deleted_at IS NULL
                LIMIT 1
                """, releaseId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return releaseRow(rows.get(0));
    }

    private Map<String, Object> getTemplateById(long templateId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, code, name, content, description, version, status, created_at, updated_at
                FROM ai_prompt_template
                WHERE id = ? AND deleted_at IS NULL
                LIMIT 1
                """, templateId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return templateRow(rows.get(0));
    }

    private List<Map<String, Object>> providerRows() {
        return jdbc.queryForList("""
                SELECT
                  id,
                  provider,
                  base_url,
                  model,
                  api_key_cipher,
                  api_key_iv,
                  api_key_tag,
                  weight,
                  status,
                  qps_limit,
                  created_at,
                  updated_at,
                  version
                FROM ai_provider_config
                WHERE deleted_at IS NULL
                ORDER BY status DESC, weight DESC, provider ASC, model ASC
                """).stream().map(this::providerRow).toList();
    }

    private Map<String, Object> getProviderById(long providerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  id,
                  provider,
                  base_url,
                  model,
                  api_key_cipher,
                  api_key_iv,
                  api_key_tag,
                  weight,
                  status,
                  qps_limit,
                  created_at,
                  updated_at,
                  version
                FROM ai_provider_config
                WHERE id = ? AND deleted_at IS NULL
                LIMIT 1
                """, providerId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return providerRow(rows.get(0));
    }

    private Map<String, Object> getAdminById(long adminId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  a.id,
                  a.username,
                  a.nickname,
                  a.role_code,
                  a.status,
                  a.last_login_at,
                  a.last_login_ip,
                  a.created_at,
                  a.updated_at,
                  r.name AS role_name,
                  r.permissions
                FROM admin_account a
                LEFT JOIN admin_role r ON r.code = a.role_code
                WHERE a.id = ? AND a.deleted_at IS NULL
                LIMIT 1
                """, adminId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return adminAccountRow(rows.get(0));
    }

    private void writeAuditLog(HttpServletRequest request, String action, String target, String detail) {
        jdbc.update("""
                INSERT INTO audit_log (
                  actor_type,
                  actor_id,
                  action,
                  target,
                  ip,
                  user_agent,
                  detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "admin",
                currentActorId(),
                action,
                target,
                requestIp(request),
                requestUserAgent(request),
                detail
        );
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "admin";
        }
        return String.valueOf(authentication.getPrincipal());
    }

    private String requestIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return stringValue(request.getRemoteAddr());
    }

    private String requestUserAgent(HttpServletRequest request) {
        return request == null ? "" : stringValue(request.getHeader("User-Agent"));
    }

    private String releaseAuditTarget(Map<String, Object> release) {
        return "app_release:" + number(release.get("id"));
    }

    private String releaseAuditDetail(
            String operation,
            Map<String, Object> subject,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        List<String> parts = new ArrayList<>();
        parts.add("operation=" + operation);
        parts.add("platform=" + stringValue(subject.get("platform")));
        parts.add("channel=" + stringValue(subject.get("channel")));
        parts.add("version=" + stringValue(subject.get("version_name")));
        parts.add("versionCode=" + number(subject.get("version_code")));
        parts.add("releaseStage=" + stringValue(subject.get("release_stage")));
        parts.add("enabled=" + booleanValue(subject.get("enabled")));
        parts.add("forceUpdate=" + booleanValue(subject.get("forceUpdate")));
        if (before != null && !before.isEmpty()) {
            parts.add("beforeVersion=" + stringValue(before.get("version_name")));
            parts.add("beforeStage=" + stringValue(before.get("release_stage")));
            parts.add("beforeEnabled=" + booleanValue(before.get("enabled")));
            parts.add("beforeForceUpdate=" + booleanValue(before.get("forceUpdate")));
        }
        if (after != null && !after.isEmpty()) {
            parts.add("afterVersion=" + stringValue(after.get("version_name")));
            parts.add("afterStage=" + stringValue(after.get("release_stage")));
            parts.add("afterEnabled=" + booleanValue(after.get("enabled")));
            parts.add("afterForceUpdate=" + booleanValue(after.get("forceUpdate")));
        }
        if (!reason.isBlank()) {
            parts.add("reason=" + reason);
        }
        return String.join("; ", parts);
    }

    private String rollbackAuditDetail(
            Map<String, Object> current,
            Map<String, Object> pausedCurrent,
            Map<String, Object> activatedTarget,
            String reason) {
        List<String> parts = new ArrayList<>();
        parts.add("operation=rollback");
        parts.add("platform=" + stringValue(current.get("platform")));
        parts.add("channel=" + stringValue(current.get("channel")));
        parts.add("fromVersion=" + stringValue(current.get("version_name")));
        parts.add("fromStage=" + stringValue(pausedCurrent.get("release_stage")));
        parts.add("toVersion=" + stringValue(activatedTarget.get("version_name")));
        parts.add("toVersionCode=" + number(activatedTarget.get("version_code")));
        parts.add("toStage=" + stringValue(activatedTarget.get("release_stage")));
        parts.add("toEnabled=" + booleanValue(activatedTarget.get("enabled")));
        parts.add("toForceUpdate=" + booleanValue(activatedTarget.get("forceUpdate")));
        if (!reason.isBlank()) {
            parts.add("reason=" + reason);
        }
        return String.join("; ", parts);
    }

    private String adminAuditDetail(String operation, Map<String, Object> before, Map<String, Object> after) {
        List<String> parts = new ArrayList<>();
        parts.add("operation=" + operation);
        if (after != null && !after.isEmpty()) {
            parts.add("username=" + stringValue(after.get("username")));
            parts.add("roleCode=" + stringValue(after.get("role_code")));
            parts.add("enabled=" + booleanValue(after.get("enabled")));
        }
        if (before != null && !before.isEmpty()) {
            parts.add("beforeRoleCode=" + stringValue(before.get("role_code")));
            parts.add("beforeEnabled=" + booleanValue(before.get("enabled")));
        }
        return String.join("; ", parts);
    }

    private String templateAuditDetail(String operation, Map<String, Object> before, Map<String, Object> after) {
        List<String> parts = new ArrayList<>();
        parts.add("operation=" + operation);
        if (after != null && !after.isEmpty()) {
            parts.add("code=" + stringValue(after.get("code")));
            parts.add("name=" + stringValue(after.get("name")));
            parts.add("enabled=" + booleanValue(after.get("enabled")));
            parts.add("version=" + number(after.get("version")));
        }
        if (before != null && !before.isEmpty()) {
            parts.add("beforeName=" + stringValue(before.get("name")));
            parts.add("beforeEnabled=" + booleanValue(before.get("enabled")));
        }
        return String.join("; ", parts);
    }

    private String providerAuditDetail(String operation, Map<String, Object> before, Map<String, Object> after) {
        List<String> parts = new ArrayList<>();
        parts.add("operation=" + operation);
        if (after != null && !after.isEmpty()) {
            parts.add("provider=" + stringValue(after.get("provider")));
            parts.add("model=" + stringValue(after.get("model")));
            parts.add("weight=" + number(after.get("weight")));
            parts.add("enabled=" + booleanValue(after.get("enabled")));
        }
        if (before != null && !before.isEmpty()) {
            parts.add("beforeWeight=" + number(before.get("weight")));
            parts.add("beforeEnabled=" + booleanValue(before.get("enabled")));
        }
        return String.join("; ", parts);
    }

    private boolean forceUpdateChanged(Map<String, Object> before, Map<String, Object> after) {
        return booleanValue(before.get("forceUpdate")) != booleanValue(after.get("forceUpdate"));
    }

    private Map<String, Object> userRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        String maskedTail = maskPhoneTail(stringValue(row.get("phone_tail")));
        out.put("phone_tail", maskedTail);
        out.put("account", accountDisplay(maskedTail, stringValue(row.get("nickname"))));
        out.put("hasCloudSync", number(row.get("has_cloud_sync")) == 1);
        out.put("activeMember", number(row.get("active_subscription_count")) > 0);
        out.put("statusText", switch ((int) number(row.get("status"))) {
            case 1 -> "正常";
            case 0 -> "禁用";
            case -1 -> "注销";
            default -> "未知";
        });
        return out;
    }

    private boolean isWithinDays(Object value, int days) {
        if (value == null) {
            return false;
        }
        LocalDateTime expiresAt;
        if (value instanceof LocalDateTime time) {
            expiresAt = time;
        } else if (value instanceof java.sql.Timestamp timestamp) {
            expiresAt = timestamp.toLocalDateTime();
        } else {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return expiresAt.isAfter(now) && !expiresAt.isAfter(now.plusDays(days));
    }

    private String maskPhoneTail(String phoneTail) {
        if (phoneTail == null) {
            return "";
        }
        String digits = phoneTail.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        String tail = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "****" + tail;
    }

    private String accountDisplay(String maskedTail, String nickname) {
        if (!maskedTail.isBlank()) {
            return maskedTail;
        }
        return nickname != null && !nickname.isBlank() ? nickname : "-";
    }

    private String platformName(String platform) {
        return switch (platform) {
            case "windows" -> "Windows";
            case "macos" -> "macOS";
            case "android" -> "Android";
            case "ios" -> "iOS";
            case "wechat" -> "微信小程序";
            case "web" -> "Web";
            default -> platform == null || platform.isBlank() ? "未知平台" : platform;
        };
    }

    private List<Map<String, Object>> platformBreakdown() {
        List<String> platforms = List.of("android", "ios", "windows", "macos", "web", "wechat");
        Map<String, Map<String, Object>> byPlatform = new LinkedHashMap<>();
        for (String platform : platforms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("platform", platform);
            item.put("platformName", platformName(platform));
            item.put("sessionCount", 0L);
            item.put("activeUsers", 0L);
            item.put("lastSeenAt", null);
            byPlatform.put(platform, item);
        }

        jdbc.queryForList("""
                SELECT
                  COALESCE(
                    NULLIF(us.platform, ''),
                    CASE
                      WHEN LOWER(us.user_agent) LIKE '%windows%' THEN 'windows'
                      WHEN LOWER(us.user_agent) LIKE '%mac os%' OR LOWER(us.user_agent) LIKE '%macintosh%' THEN 'macos'
                      WHEN LOWER(us.user_agent) LIKE '%android%' THEN 'android'
                      WHEN LOWER(us.user_agent) LIKE '%iphone%' OR LOWER(us.user_agent) LIKE '%ipad%' OR LOWER(us.user_agent) LIKE '%ios%' THEN 'ios'
                      WHEN LOWER(us.user_agent) LIKE '%micromessenger%' THEN 'wechat'
                      ELSE 'web'
                    END
                  ) AS platform,
                  COUNT(*) AS sessionCount,
                  COUNT(DISTINCT us.user_id) AS activeUsers,
                  MAX(us.created_at) AS lastSeenAt
                FROM user_session us
                GROUP BY platform
                """).forEach(row -> {
            String platform = stringValue(row.get("platform"));
            Map<String, Object> item = byPlatform.get(platform);
            if (item != null) {
                item.put("sessionCount", number(row.get("sessionCount")));
                item.put("activeUsers", number(row.get("activeUsers")));
                item.put("lastSeenAt", row.get("lastSeenAt"));
            }
        });
        return new ArrayList<>(byPlatform.values());
    }

    private String reminderTypeName(String type) {
        return switch (type) {
            case "meal" -> "饮食";
            case "exercise" -> "运动";
            case "medicine" -> "用药";
            case "weight" -> "体重";
            default -> type == null || type.isBlank() ? "未知类型" : type;
        };
    }

    private void validateReleasePayload(Map<String, Object> payload) {
        String platform = normalizedText(payload.get("platform"));
        String channel = normalizedText(payload.get("channel"));
        String versionName = normalizedText(payload.get("versionName"));
        String releaseStage = normalizedText(payload.get("releaseStage"));
        String packageUrl = normalizedText(payload.get("packageUrl"));
        String minSupportedVersion = normalizedText(payload.get("minSupportedVersion"));
        String releaseNotes = normalizedText(payload.get("releaseNotes"));
        int versionCode = intValue(payload.get("versionCode"));
        int rolloutPercent = intValue(payload.get("rolloutPercent"));

        List<String> platforms = List.of("windows", "macos", "android", "ios", "wechat", "web");
        List<String> stages = List.of("draft", "gray", "release", "paused");

        if (!platforms.contains(platform)) {
            throw new IllegalArgumentException("platform 不合法");
        }
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel 不能为空");
        }
        if (versionName.isBlank()) {
            throw new IllegalArgumentException("versionName 不能为空");
        }
        if (versionCode <= 0) {
            throw new IllegalArgumentException("versionCode 必须大于 0");
        }
        if (!stages.contains(releaseStage)) {
            throw new IllegalArgumentException("releaseStage 不合法");
        }
        if (rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("rolloutPercent 必须在 0 到 100 之间");
        }
        if (packageUrl.isBlank()) {
            throw new IllegalArgumentException("packageUrl 不能为空");
        }
        if (minSupportedVersion.isBlank()) {
            throw new IllegalArgumentException("minSupportedVersion 不能为空");
        }
        if (releaseNotes.isBlank()) {
            throw new IllegalArgumentException("releaseNotes 不能为空");
        }
    }

    private void validateTemplatePayload(Map<String, Object> payload, boolean createMode) {
        String code = normalizedText(payload.get("code"));
        String name = normalizedText(payload.get("name"));
        String content = normalizedText(payload.get("content"));
        if (createMode && code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
    }

    private void validateProviderPayload(Map<String, Object> payload, boolean createMode) {
        String provider = normalizedText(payload.get("provider"));
        String baseUrl = normalizedText(payload.get("baseUrl"));
        String model = normalizedText(payload.get("model"));
        String apiKey = normalizedText(payload.get("apiKey"));
        int weight = intValue(payload.get("weight"));
        int qpsLimit = intValue(payload.get("qpsLimit"));
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("model 不能为空");
        }
        if (createMode && apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }
        if (weight < 0 || weight > 1000) {
            throw new IllegalArgumentException("weight 必须在 0 到 1000 之间");
        }
        if (qpsLimit < 0 || qpsLimit > 100000) {
            throw new IllegalArgumentException("qpsLimit 不合法");
        }
    }

    private long count(String sql, Object... args) {
        Long value = queryLong(sql, args);
        return value != null ? value : 0L;
    }

    private Long queryLong(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long sumFen(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value != null ? value : 0L;
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int intValue(Object value) {
        return (int) number(value);
    }

    private Long nullableLong(Object value) {
        String text = normalizedText(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        String text = stringValue(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private String fallbackText(Object value, String fallback) {
        String text = normalizedText(value);
        return text.isBlank() ? fallback : text;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = normalizedText(value);
        return text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private LocalDateTime releaseTime(Object value) {
        String text = normalizedText(value);
        if (text.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(text.replace(" ", "T"));
    }

    private String normalizedText(Object value) {
        return stringValue(value).trim();
    }

    private void validateAdminPayload(Map<String, Object> payload, boolean createMode) {
        String username = normalizedText(payload.get("username"));
        String roleCode = normalizedText(payload.get("roleCode"));
        String password = normalizedText(payload.get("password"));
        List<String> allowedRoles = List.of("super_admin", "operator", "auditor");

        if (createMode && username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        if (createMode && password.length() < 6) {
            throw new IllegalArgumentException("password 不能少于 6 位");
        }
        if (!createMode && !password.isBlank() && password.length() < 6) {
            throw new IllegalArgumentException("password 不能少于 6 位");
        }
        if (!allowedRoles.contains(roleCode)) {
            throw new IllegalArgumentException("roleCode 不合法");
        }
    }

    private int compareVersionCode(String versionName) {
        if (versionName == null || versionName.isBlank()) {
            return 0;
        }
        String[] parts = versionName.trim().split("\\.");
        int result = 0;
        for (int i = 0; i < parts.length && i < 4; i++) {
            int part = 0;
            try {
                part = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
            }
            result = result * 1000 + part;
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String moneyYuan(long amountFen) {
        return BigDecimal.valueOf(amountFen, 2).toPlainString();
    }

    private String percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.00";
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
