package io.healthresetplan.modules.content;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentInteractionService {

    private final JdbcTemplate jdbc;

    public ContentInteractionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> get(String userId, long contentId) {
        requirePublished(contentId);
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COALESCE(SUM(reaction = 'like'), 0) AS likeCount,
                       COALESCE(SUM(reaction = 'dislike'), 0) AS dislikeCount
                FROM health_content_reaction
                WHERE content_id = ?
                """, contentId);
        List<String> reactions = jdbc.queryForList("""
                SELECT reaction FROM health_content_reaction
                WHERE content_id = ? AND user_id = ?
                """, String.class, contentId, userId);
        List<Map<String, Object>> comments = jdbc.queryForList("""
                SELECT c.id,
                       COALESCE(NULLIF(u.nickname, ''), '健康用户') AS authorName,
                       c.content, c.created_at AS createdAt,
                       CASE WHEN c.user_id = ? THEN 1 ELSE 0 END AS isMine
                FROM health_content_comment c
                JOIN user_account u ON u.user_id = c.user_id
                WHERE c.content_id = ?
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT 100
                """, userId, contentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeCount", counts.get("likeCount"));
        result.put("dislikeCount", counts.get("dislikeCount"));
        result.put("userReaction", reactions.isEmpty() ? "" : reactions.get(0));
        result.put("comments", comments);
        return result;
    }

    @Transactional
    public Map<String, Object> react(String userId, long contentId, String reaction) {
        requirePublished(contentId);
        String value = reaction == null ? "" : reaction.trim();
        if (value.isEmpty()) {
            jdbc.update("DELETE FROM health_content_reaction WHERE user_id = ? AND content_id = ?",
                    userId, contentId);
        } else if ("like".equals(value) || "dislike".equals(value)) {
            jdbc.update("""
                    INSERT INTO health_content_reaction (user_id, content_id, reaction)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE reaction = VALUES(reaction), updated_at = NOW(3)
                    """, userId, contentId, value);
        } else {
            throw new BusinessException(40001, "互动类型不正确");
        }
        return get(userId, contentId);
    }

    @Transactional
    public Map<String, Object> addComment(String userId, long contentId, String content) {
        requirePublished(contentId);
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) throw new BusinessException(40001, "评论内容不能为空");
        if (value.length() > 500) throw new BusinessException(40001, "评论不能超过500字");
        jdbc.update("""
                INSERT INTO health_content_comment (user_id, content_id, content)
                VALUES (?, ?, ?)
                """, userId, contentId, value);
        return get(userId, contentId);
    }

    @Transactional
    public Map<String, Object> deleteComment(String userId, long contentId, long commentId) {
        requirePublished(contentId);
        int deleted = jdbc.update("""
                DELETE FROM health_content_comment
                WHERE id = ? AND content_id = ? AND user_id = ?
                """, commentId, contentId, userId);
        if (deleted == 0) throw new BusinessException(40401, "评论不存在或无权删除");
        return get(userId, contentId);
    }

    private void requirePublished(long contentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_content WHERE id = ? AND status = 'published'",
                Integer.class, contentId);
        if (count == null || count == 0) {
            throw new BusinessException(40401, "资讯不存在或已下架");
        }
    }
}
