package io.healthresetplan.modules.ai;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AiConsentService {
    public static final String POLICY_VERSION = "2026-07-17";
    private final JdbcTemplate jdbc;

    public AiConsentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireActive(String userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_user_consent
                WHERE user_id = ? AND policy_version = ? AND revoked_at IS NULL
                """, Long.class, userId, POLICY_VERSION);
        if (count == null || count == 0) {
            throw new BusinessException(40302, "请先阅读并同意 AI 数据处理说明");
        }
    }

    public Map<String, Object> status(String userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_user_consent
                WHERE user_id = ? AND policy_version = ? AND revoked_at IS NULL
                """, Long.class, userId, POLICY_VERSION);
        return Map.of("accepted", count != null && count > 0, "policyVersion", POLICY_VERSION);
    }

    public void accept(String userId) {
        jdbc.update("""
                INSERT INTO ai_user_consent (user_id, policy_version, accepted_at, revoked_at)
                VALUES (?, ?, ?, NULL)
                ON DUPLICATE KEY UPDATE accepted_at = VALUES(accepted_at), revoked_at = NULL
                """, userId, POLICY_VERSION, LocalDateTime.now());
    }

    public void revoke(String userId) {
        jdbc.update("UPDATE ai_user_consent SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                LocalDateTime.now(), userId);
    }
}
