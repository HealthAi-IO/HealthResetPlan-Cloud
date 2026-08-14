package io.healthresetplan.modules.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.modules.content.dto.ContentUpsertRequest;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentService {

    private static final Set<String> TYPES = Set.of("article", "card", "qa", "todo");
    private static final Set<String> EDITABLE_STATUSES = Set.of("draft", "pending_review");
    private static final Safelist ARTICLE_HTML = Safelist.relaxed()
            .addTags("section", "figure", "figcaption")
            .addAttributes("img", "loading")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ContentService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> listPublished(String userId, String type, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 30);
        String typeFilter = type == null || type.isBlank() ? "" : type.trim().toLowerCase();
        if (!typeFilter.isEmpty()) requireType(typeFilter);

        String typeSql = typeFilter.isEmpty() ? "" : " AND c.type = ? ";
        Object[] listArgs = typeFilter.isEmpty()
                ? new Object[]{userId, safeSize, (safePage - 1) * safeSize}
                : new Object[]{userId, typeFilter, safeSize, (safePage - 1) * safeSize};
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT c.id, c.type, c.title, c.summary, c.cover_url AS coverUrl,
                       c.published_at AS publishedAt,
                       CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS `read`
                FROM health_content c
                LEFT JOIN health_content_read r ON r.content_id = c.id AND r.user_id = ?
                WHERE c.status = 'published'
                """ + typeSql + """
                ORDER BY c.published_at DESC, c.id DESC
                LIMIT ? OFFSET ?
                """, listArgs);
        Long total = typeFilter.isEmpty()
                ? jdbc.queryForObject("SELECT COUNT(*) FROM health_content WHERE status = 'published'", Long.class)
                : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM health_content WHERE status = 'published' AND type = ?",
                        Long.class, typeFilter);
        return page(items, total, safePage, safeSize);
    }

    public Map<String, Object> publishedDetail(String userId, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.type, c.title, c.summary, c.cover_url AS coverUrl,
                       c.cover_prompt AS coverPrompt, c.body_html AS bodyHtml,
                       c.content_json AS content, c.source_type AS sourceType,
                       c.published_at AS publishedAt,
                       CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS `read`
                FROM health_content c
                LEFT JOIN health_content_read r ON r.content_id = c.id AND r.user_id = ?
                WHERE c.id = ? AND c.status = 'published'
                """, userId, id);
        if (rows.isEmpty()) throw new BusinessException(40401, "资讯不存在或已下架");
        return normalizeContent(rows.get(0));
    }

    @Transactional
    public void markRead(String userId, long contentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_content WHERE id = ? AND status = 'published'",
                Integer.class, contentId);
        if (count == null || count == 0) throw new BusinessException(40401, "资讯不存在或已下架");
        jdbc.update("""
                INSERT INTO health_content_read (user_id, content_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE last_read_at = CURRENT_TIMESTAMP(3)
                """, userId, contentId);
    }

    public Map<String, Object> listMessages(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT m.id, m.content_id AS contentId, m.type, m.title, m.body,
                       m.status, m.read_at AS readAt, m.created_at AS createdAt,
                       COALESCE(c.status, 'offline') AS contentStatus
                FROM site_message m
                LEFT JOIN health_content c ON c.id = m.content_id
                WHERE m.user_id = ? AND (m.expires_at IS NULL OR m.expires_at > NOW(3))
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ? OFFSET ?
                """, userId, safeSize, (safePage - 1) * safeSize);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM site_message
                WHERE user_id = ? AND (expires_at IS NULL OR expires_at > NOW(3))
                """, Long.class, userId);
        return page(items, total, safePage, safeSize);
    }

    public long unreadMessageCount(String userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM site_message
                WHERE user_id = ? AND status = 'unread'
                  AND (expires_at IS NULL OR expires_at > NOW(3))
                """, Long.class, userId);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markMessageRead(String userId, long id) {
        int updated = jdbc.update("""
                UPDATE site_message SET status = 'read', read_at = COALESCE(read_at, NOW(3))
                WHERE id = ? AND user_id = ?
                """, id, userId);
        if (updated == 0) throw new BusinessException(40401, "消息不存在");
    }

    @Transactional
    public void markAllMessagesRead(String userId) {
        jdbc.update("""
                UPDATE site_message SET status = 'read', read_at = NOW(3)
                WHERE user_id = ? AND status = 'unread'
                """, userId);
    }

    public Map<String, Object> adminList(String type, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (type != null && !type.isBlank()) {
            requireType(type);
            where.append(" AND type = ? ");
            args.add(type);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            args.add(status);
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_content" + where, Long.class, args.toArray());
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id, type, title, summary, cover_url AS coverUrl, status,
                       source_type AS sourceType, ai_provider AS aiProvider,
                       published_at AS publishedAt, created_at AS createdAt,
                       updated_at AS updatedAt
                FROM health_content
                """ + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", args.toArray());
        return page(items, total, safePage, safeSize);
    }

    public Map<String, Object> adminDetail(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, type, title, summary, cover_url AS coverUrl,
                       cover_prompt AS coverPrompt, body_html AS bodyHtml,
                       content_json AS content, status, source_type AS sourceType,
                       ai_provider AS aiProvider, ai_model AS aiModel,
                       scheduled_publish_at AS scheduledPublishAt,
                       published_at AS publishedAt, created_at AS createdAt,
                       updated_at AS updatedAt
                FROM health_content WHERE id = ?
                """, id);
        if (rows.isEmpty()) throw new BusinessException(40401, "资讯不存在");
        return normalizeContent(rows.get(0));
    }

    @Transactional
    public long create(ContentUpsertRequest request, long adminId) {
        PreparedContent content = prepare(request, true);
        try {
            jdbc.update("""
                    INSERT INTO health_content (
                        type, title, summary, cover_url, cover_prompt, body_html,
                        content_json, status, source_type, content_hash, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, 'manual', ?, ?)
                    """, content.type(), content.title(), content.summary(), content.coverUrl(),
                    content.coverPrompt(), content.bodyHtml(), content.contentJson(),
                    content.status(), content.hash(), adminId);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(40901, "存在相同内容，请勿重复创建");
        }
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional
    public void update(long id, ContentUpsertRequest request) {
        Map<String, Object> existing = adminDetail(id);
        if (!EDITABLE_STATUSES.contains(String.valueOf(existing.get("status")))) {
            throw new BusinessException(40901, "已发布或已下架资讯不能直接编辑");
        }
        PreparedContent content = prepare(request, true);
        try {
            jdbc.update("""
                    UPDATE health_content
                    SET type = ?, title = ?, summary = ?, cover_url = ?, cover_prompt = ?,
                        body_html = ?, content_json = CAST(? AS JSON), status = ?,
                        content_hash = ?, version = version + 1
                    WHERE id = ?
                    """, content.type(), content.title(), content.summary(), content.coverUrl(),
                    content.coverPrompt(), content.bodyHtml(), content.contentJson(),
                    content.status(), content.hash(), id);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(40901, "存在相同内容，请勿重复保存");
        }
    }

    @Transactional
    public void publish(long id, long adminId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title, summary, status FROM health_content WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(40401, "资讯不存在");
        if ("published".equals(rows.get(0).get("status"))) return;
        jdbc.update("""
                UPDATE health_content
                SET status = 'published', published_at = NOW(3), reviewed_by = ?, version = version + 1
                WHERE id = ?
                """, adminId > 0 ? adminId : null, id);
        createMessages(id, String.valueOf(rows.get(0).get("title")),
                String.valueOf(rows.get(0).get("summary")));
    }

    @Transactional
    public void takeOffline(long id) {
        int updated = jdbc.update("""
                UPDATE health_content SET status = 'offline', version = version + 1
                WHERE id = ? AND status = 'published'
                """, id);
        if (updated == 0) throw new BusinessException(40901, "只有已发布资讯可以下架");
    }

    @Transactional
    public void deleteDraft(long id) {
        int deleted = jdbc.update(
                "DELETE FROM health_content WHERE id = ? AND status IN ('draft', 'pending_review')", id);
        if (deleted == 0) throw new BusinessException(40901, "只有草稿或待审核资讯可以删除");
    }

    @Transactional
    public long createAiContent(
            String type, String title, String summary, String coverPrompt,
            String bodyHtml, Object content, String provider, String model, boolean autoPublish) {
        ContentUpsertRequest request = new ContentUpsertRequest(
                type, title, summary, "", coverPrompt, bodyHtml, content,
                autoPublish ? "draft" : "pending_review");
        PreparedContent prepared = prepare(request, false);
        if (isSimilarToRecentTitle(prepared.title())) {
            throw new BusinessException(40901, "内容与近180天资讯过于相似");
        }
        try {
            jdbc.update("""
                    INSERT INTO health_content (
                        type, title, summary, cover_url, cover_prompt, body_html,
                        content_json, status, source_type, ai_provider, ai_model, content_hash
                    ) VALUES (?, ?, ?, '', ?, ?, CAST(? AS JSON), ?, 'ai', ?, ?, ?)
                    """, prepared.type(), prepared.title(), prepared.summary(), prepared.coverPrompt(),
                    prepared.bodyHtml(), prepared.contentJson(), prepared.status(),
                    provider == null ? "" : provider, model == null ? "" : model, prepared.hash());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(40901, "AI生成内容与已有资讯重复");
        }
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (autoPublish) publish(id, 0);
        return id;
    }

    private void createMessages(long contentId, String title, String summary) {
        jdbc.update("""
                INSERT IGNORE INTO site_message (
                    user_id, content_id, type, title, body, expires_at
                )
                SELECT user_id, ?, 'content_published', ?, ?, DATE_ADD(NOW(3), INTERVAL 180 DAY)
                FROM user_account
                WHERE status = 1 AND deleted_at IS NULL
                """, contentId, "新健康资讯：" + title, summary == null ? "" : summary);
    }

    private PreparedContent prepare(ContentUpsertRequest request, boolean enforceSafety) {
        String type = request.type().trim().toLowerCase();
        requireType(type);
        String title = request.title().trim();
        String summary = text(request.summary());
        String coverUrl = text(request.coverUrl());
        String coverPrompt = text(request.coverPrompt());
        String bodyHtml = "article".equals(type)
                ? sanitizeArticleHtml(text(request.bodyHtml()))
                : "";
        if ("article".equals(type) && bodyHtml.isBlank()) {
            throw new BusinessException(40001, "图文资讯正文不能为空");
        }
        String contentJson = json(request.content() == null ? Map.of() : request.content());
        if ("card".equals(type)) {
            validateCard(request.content());
        }
        String status = text(request.status()).isBlank() ? "draft" : request.status().trim();
        if (!EDITABLE_STATUSES.contains(status)) status = "draft";
        String safetyText = title + summary + bodyHtml + contentJson;
        if (enforceSafety && !ContentSafetyGuard.isSafe(safetyText)) {
            throw new BusinessException(40001, "内容包含诊疗、药效或用药建议相关描述，请修改后再保存");
        }
        String hash = HashUtils.sha256Hex(type + "|" + title + "|" + summary + "|" + bodyHtml + "|" + contentJson);
        return new PreparedContent(
                type, title, summary, coverUrl, coverPrompt, bodyHtml, contentJson, status, hash);
    }

    private void validateCard(Object content) {
        if (!(content instanceof Map<?, ?> map)) {
            throw new BusinessException(40001, "科普卡片内容格式不正确");
        }
        Object points = map.get("points");
        if (!(points instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(40001, "科普卡片至少需要一个要点");
        }
    }

    private boolean isSimilarToRecentTitle(String title) {
        List<String> titles = jdbc.queryForList("""
                SELECT title FROM health_content
                WHERE created_at >= DATE_SUB(NOW(3), INTERVAL 180 DAY)
                """, String.class);
        return titles.stream().anyMatch(existing -> similarity(existing, title) >= 0.60);
    }

    static double similarity(String left, String right) {
        String normalizedLeft = normalizeTitle(left);
        String normalizedRight = normalizeTitle(right);
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) {
            return normalizedLeft.equals(normalizedRight) ? 1 : 0;
        }
        long intersection = a.stream().filter(b::contains).count();
        double jaccard = (double) intersection / (a.size() + b.size() - intersection);
        int maxLength = Math.max(normalizedLeft.length(), normalizedRight.length());
        double editSimilarity = maxLength == 0
                ? 1
                : 1 - (double) editDistance(normalizedLeft, normalizedRight) / maxLength;
        return Math.max(jaccard, editSimilarity);
    }

    static String sanitizeArticleHtml(String html) {
        return Jsoup.clean(html, "https://content.local/", ARTICLE_HTML);
    }

    private static Set<String> bigrams(String value) {
        String text = normalizeTitle(value);
        java.util.HashSet<String> result = new java.util.HashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) result.add(text.substring(i, i + 2));
        return result;
    }

    private static String normalizeTitle(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）]", "");
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int replace = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), replace);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private Map<String, Object> normalizeContent(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        Object raw = normalized.get("content");
        if (raw != null && !(raw instanceof Map) && !(raw instanceof List)) {
            try {
                String json = raw instanceof byte[] bytes
                        ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                        : String.valueOf(raw);
                normalized.put("content", objectMapper.readValue(json, Object.class));
            } catch (JsonProcessingException ignored) {
                normalized.put("content", Map.of());
            }
        }
        return normalized;
    }

    private Map<String, Object> page(List<Map<String, Object>> items, Long total, int page, int size) {
        return Map.of(
                "items", items,
                "total", total == null ? 0 : total,
                "page", page,
                "size", size);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(40001, "内容JSON格式不正确");
        }
    }

    private void requireType(String type) {
        if (!TYPES.contains(type)) throw new BusinessException(40001, "不支持的资讯类型");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record PreparedContent(
            String type,
            String title,
            String summary,
            String coverUrl,
            String coverPrompt,
            String bodyHtml,
            String contentJson,
            String status,
            String hash) {
    }
}
