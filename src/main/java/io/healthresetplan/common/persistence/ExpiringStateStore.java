package io.healthresetplan.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ExpiringStateStore {

    private final JdbcTemplate jdbc;

    public ExpiringStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void put(String key, String value, Duration ttl) {
        jdbc.update("""
                INSERT INTO app_ephemeral_state (state_key, state_value, expires_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    state_value = VALUES(state_value),
                    expires_at = VALUES(expires_at)
                """, key, value, expiresAt(ttl));
    }

    public String get(String key) {
        List<String> values = jdbc.query(
                "SELECT state_value FROM app_ephemeral_state "
                        + "WHERE state_key = ? AND expires_at > CURRENT_TIMESTAMP(3)",
                (rs, rowNum) -> rs.getString(1),
                key);
        return values.isEmpty() ? null : values.get(0);
    }

    @Transactional
    public String take(String key) {
        List<String> values = jdbc.query(
                "SELECT state_value FROM app_ephemeral_state "
                        + "WHERE state_key = ? AND expires_at > CURRENT_TIMESTAMP(3) FOR UPDATE",
                (rs, rowNum) -> rs.getString(1),
                key);
        jdbc.update("DELETE FROM app_ephemeral_state WHERE state_key = ?", key);
        return values.isEmpty() ? null : values.get(0);
    }

    public boolean putIfAbsent(String key, String value, Duration ttl) {
        int affected = jdbc.update("""
                INSERT INTO app_ephemeral_state (state_key, state_value, expires_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    state_value = IF(expires_at <= CURRENT_TIMESTAMP(3), VALUES(state_value), state_value),
                    expires_at = IF(expires_at <= CURRENT_TIMESTAMP(3), VALUES(expires_at), expires_at)
                """, key, value, expiresAt(ttl));
        return affected > 0;
    }

    @Transactional
    public long increment(String key, long delta, Duration ttl) {
        jdbc.update("""
                INSERT INTO app_ephemeral_state (state_key, state_value, expires_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    state_value = IF(
                        expires_at <= CURRENT_TIMESTAMP(3),
                        VALUES(state_value),
                        CAST(state_value AS SIGNED) + VALUES(state_value)
                    ),
                    expires_at = IF(
                        expires_at <= CURRENT_TIMESTAMP(3),
                        VALUES(expires_at),
                        expires_at
                    )
                """, key, Long.toString(delta), expiresAt(ttl));
        return Long.parseLong(jdbc.queryForObject(
                "SELECT state_value FROM app_ephemeral_state WHERE state_key = ?",
                String.class,
                key));
    }

    public void delete(String key) {
        jdbc.update("DELETE FROM app_ephemeral_state WHERE state_key = ?", key);
    }

    @Scheduled(cron = "0 20 * * * *")
    public void deleteExpired() {
        jdbc.update("DELETE FROM app_ephemeral_state WHERE expires_at <= CURRENT_TIMESTAMP(3)");
    }

    private Timestamp expiresAt(Duration ttl) {
        return Timestamp.from(Instant.now().plus(ttl));
    }
}
