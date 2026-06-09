package io.healthresetplan.modules.admin;

import io.healthresetplan.common.result.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "totalUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL"),
                "todayNewUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL AND created_at >= ?", today),
                "activeMembers", count("SELECT COUNT(DISTINCT user_id) FROM user_subscription WHERE status = 'active' AND expires_at > NOW(3)"),
                "cloudSyncUsers", count("SELECT COUNT(*) FROM user_account WHERE deleted_at IS NULL AND has_cloud_sync = 1"),
                "healthIndicators", count("SELECT COUNT(*) FROM health_indicator WHERE deleted_at IS NULL"),
                "reports", count("SELECT COUNT(*) FROM health_report WHERE deleted_at IS NULL"),
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
            where += " AND (ua.user_id LIKE ? OR ua.nickname LIKE ? OR ua.custom_id LIKE ?) ";
            countArgs = new Object[]{like, like, like};
            listArgs = new Object[]{like, like, like, safeSize, offset};
        } else {
            listArgs = new Object[]{safeSize, offset};
        }

        Long total = queryLong("SELECT COUNT(*) FROM user_account ua" + where, countArgs);
        String sql = """
                SELECT
                  ua.user_id,
                  ua.custom_id,
                  ua.nickname,
                  ua.avatar_url,
                  ua.status,
                  ua.has_cloud_sync,
                  ua.created_at,
                  ua.updated_at,
                  (SELECT MAX(us.created_at) FROM user_session us WHERE us.user_id = ua.user_id) AS last_login_at,
                  (SELECT COUNT(*) FROM health_indicator hi WHERE hi.user_id = ua.user_id AND hi.deleted_at IS NULL) AS indicator_count,
                  (SELECT COUNT(*) FROM health_report hr WHERE hr.user_id = ua.user_id AND hr.deleted_at IS NULL) AS report_count,
                  (SELECT COUNT(*) FROM user_subscription sub WHERE sub.user_id = ua.user_id AND sub.status = 'active' AND sub.expires_at > NOW(3)) AS active_subscription_count,
                  (SELECT MAX(sub.expires_at) FROM user_subscription sub WHERE sub.user_id = ua.user_id AND sub.status = 'active') AS member_expires_at
                FROM user_account ua
                """ + where + """
                ORDER BY ua.created_at DESC
                LIMIT ? OFFSET ?
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
                  ua.nickname,
                  ua.avatar_url,
                  ua.status,
                  ua.has_cloud_sync,
                  ua.created_at,
                  ua.updated_at,
                  (SELECT MAX(us.created_at) FROM user_session us WHERE us.user_id = ua.user_id) AS last_login_at,
                  (SELECT COUNT(*) FROM health_indicator hi WHERE hi.user_id = ua.user_id AND hi.deleted_at IS NULL) AS indicator_count,
                  (SELECT COUNT(*) FROM health_report hr WHERE hr.user_id = ua.user_id AND hr.deleted_at IS NULL) AS report_count,
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
                  SELECT DATE(created_at) AS day, COUNT(*) AS indicators
                  FROM health_indicator
                  WHERE deleted_at IS NULL AND created_at >= ?
                  GROUP BY DATE(created_at)
                ) h ON h.day = d.day
                LEFT JOIN (
                  SELECT DATE(created_at) AS day, COUNT(*) AS orders
                  FROM payment_order
                  WHERE status = 'paid' AND created_at >= ?
                  GROUP BY DATE(created_at)
                ) o ON o.day = d.day
                ORDER BY d.day ASC
                """, start, start, start);
        return rows.stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("day", String.valueOf(row.get("day")));
            out.put("users", number(row.get("users")));
            out.put("indicators", number(row.get("indicators")));
            out.put("orders", number(row.get("orders")));
            return out;
        }).toList();
    }

    private List<Map<String, Object>> indicatorTypes(String userId) {
        if (userId == null || userId.isBlank()) {
            return jdbc.queryForList("""
                    SELECT type, COUNT(*) AS count
                    FROM health_indicator
                    WHERE deleted_at IS NULL
                    GROUP BY type
                    ORDER BY count DESC
                    LIMIT 12
                    """);
        }
        return jdbc.queryForList("""
                SELECT type, COUNT(*) AS count
                FROM health_indicator
                WHERE deleted_at IS NULL AND user_id = ?
                GROUP BY type
                ORDER BY count DESC
                LIMIT 12
                """, userId);
    }

    private List<Map<String, Object>> recentUsers(int limit) {
        return jdbc.queryForList("""
                SELECT user_id, custom_id, nickname, status, has_cloud_sync, created_at
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

    private Map<String, Object> userRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
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

    private long count(String sql, Object... args) {
        Long value = queryLong(sql, args);
        return value != null ? value : 0L;
    }

    private Long queryLong(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long sumFen(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value != null ? value : 0L;
    }

    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0L;
        return Long.parseLong(String.valueOf(value));
    }

    private String moneyYuan(long amountFen) {
        return BigDecimal.valueOf(amountFen, 2).toPlainString();
    }
}
