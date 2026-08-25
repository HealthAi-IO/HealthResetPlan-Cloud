package io.healthresetplan.modules.admin;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/operations")
public class AdminOperationsController {

    private final JdbcTemplate jdbc;

    public AdminOperationsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/ai-summary")
    public R<Map<String, Object>> aiSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(balance), 0) AS available_credits,
                  COALESCE(SUM(consumed_total), 0) AS consumed_credits,
                  COUNT(*) AS credit_users,
                  COALESCE(SUM(CASE WHEN updated_at >= CURDATE() THEN consumed_total ELSE 0 END), 0) AS active_credit_total
                FROM ai_credit_account
                """));
        result.put("features", jdbc.queryForList("""
                SELECT COALESCE(feature_code, '未分类') AS feature_code, COUNT(*) AS usage_count
                FROM ai_credit_ledger
                WHERE reason = 'consume'
                GROUP BY feature_code
                ORDER BY usage_count DESC
                """));
        return R.ok(result);
    }

    @GetMapping("/comments")
    public R<Map<String, Object>> comments(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 10), 100);
        int offset = (safePage - 1) * safeSize;
        String like = "%" + keyword.trim() + "%";
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT c.id, c.content_id, h.title AS content_title, c.user_id,
                       COALESCE(NULLIF(u.nickname, ''), '健康用户') AS nickname,
                       c.content, c.created_at
                FROM health_content_comment c
                JOIN health_content h ON h.id = c.content_id
                LEFT JOIN user_account u ON u.user_id = c.user_id
                WHERE (? = '' OR c.content LIKE ? OR h.title LIKE ?)
                ORDER BY c.id DESC
                LIMIT ? OFFSET ?
                """, keyword.trim(), like, like, safeSize, offset);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM health_content_comment c
                JOIN health_content h ON h.id = c.content_id
                WHERE (? = '' OR c.content LIKE ? OR h.title LIKE ?)
                """, Long.class, keyword.trim(), like, like);
        return R.ok(Map.of("items", items, "total", total == null ? 0 : total));
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable long id, Authentication authentication,
                                 HttpServletRequest request) {
        int updated = jdbc.update("DELETE FROM health_content_comment WHERE id = ?", id);
        if (updated == 0) throw new BusinessException(40401, "评论不存在");
        audit(authentication, request, "content_comment_deleted", "comment:" + id, "管理员删除资讯评论");
        return R.ok();
    }

    @GetMapping("/messages")
    public R<Map<String, Object>> messages() {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT title, body, type, created_at, COUNT(*) AS recipient_count,
                       SUM(CASE WHEN status = 'read' THEN 1 ELSE 0 END) AS read_count
                FROM site_message
                WHERE type = 'system_notice'
                GROUP BY title, body, type, created_at
                ORDER BY created_at DESC
                LIMIT 100
                """);
        return R.ok(Map.of("items", items));
    }

    @PostMapping("/messages")
    public R<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> body,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        String title = text(body.get("title"));
        String message = text(body.get("body"));
        if (title.isBlank() || title.length() > 160) throw new BusinessException(40001, "通知标题长度应为 1-160 字");
        if (message.isBlank() || message.length() > 500) throw new BusinessException(40001, "通知正文长度应为 1-500 字");
        int recipients = jdbc.update("""
                INSERT INTO site_message (user_id, type, title, body, status, created_at, updated_at)
                SELECT user_id, 'system_notice', ?, ?, 'unread', NOW(3), NOW(3)
                FROM user_account WHERE deleted_at IS NULL
                """, title, message);
        audit(authentication, request, "site_message_sent", "site_message:broadcast", "接收人数=" + recipients);
        return R.ok(Map.of("recipientCount", recipients, "sentAt", LocalDateTime.now()));
    }

    private void audit(Authentication authentication, HttpServletRequest request,
                       String action, String target, String detail) {
        String actor = authentication == null ? "admin" : String.valueOf(authentication.getPrincipal());
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        String userAgent = text(request.getHeader("User-Agent"));
        jdbc.update("""
                INSERT INTO audit_log (actor_type, actor_id, action, target, ip, user_agent, detail)
                VALUES ('admin', ?, ?, ?, ?, ?, ?)
                """, actor, action, target, ip, userAgent.substring(0, Math.min(userAgent.length(), 255)), detail);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
