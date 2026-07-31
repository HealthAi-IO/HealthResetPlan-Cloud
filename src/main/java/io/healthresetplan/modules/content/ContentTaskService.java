package io.healthresetplan.modules.content;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.content.dto.ContentTaskRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentTaskService {

    private static final Set<String> TYPES = Set.of("card");
    private static final Set<String> SCHEDULES = Set.of("daily", "weekly");
    private static final Set<String> MODES = Set.of("auto");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    public ContentTaskService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT id, name, content_type AS contentType, topic,
                       schedule_type AS scheduleType, day_of_week AS dayOfWeek,
                       publish_time AS publishTime, publish_mode AS publishMode,
                       preferred_provider AS preferredProvider,
                       image_enabled AS imageEnabled, enabled, next_run_at AS nextRunAt,
                       last_run_at AS lastRunAt, last_result AS lastResult,
                       last_error AS lastError, updated_at AS updatedAt
                FROM ai_content_task ORDER BY id
                """);
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, content_type AS contentType, topic,
                       schedule_type AS scheduleType, day_of_week AS dayOfWeek,
                       publish_time AS publishTime, publish_mode AS publishMode,
                       preferred_provider AS preferredProvider,
                       image_enabled AS imageEnabled, enabled, next_run_at AS nextRunAt
                FROM ai_content_task WHERE id = ?
                """, id);
        if (rows.isEmpty()) throw new BusinessException(40401, "AI生成任务不存在");
        return rows.get(0);
    }

    @Transactional
    public long create(ContentTaskRequest request, long adminId) {
        validate(request);
        LocalDateTime nextRunAt = request.enabled() ? nextRun(request, now()) : null;
        jdbc.update("""
                INSERT INTO ai_content_task (
                    name, content_type, topic, schedule_type, day_of_week,
                    publish_time, publish_mode, preferred_provider,
                    image_enabled, enabled, next_run_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.name().trim(), request.contentType(), request.topic().trim(),
                request.scheduleType(), request.dayOfWeek(), Time.valueOf(request.publishTime()),
                request.publishMode(), text(request.preferredProvider()),
                request.imageEnabled(), request.enabled(), nextRunAt, adminId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional
    public void update(long id, ContentTaskRequest request) {
        validate(request);
        get(id);
        LocalDateTime nextRunAt = request.enabled() ? nextRun(request, now()) : null;
        jdbc.update("""
                UPDATE ai_content_task
                SET name = ?, content_type = ?, topic = ?, schedule_type = ?,
                    day_of_week = ?, publish_time = ?, publish_mode = ?,
                    preferred_provider = ?, image_enabled = ?, enabled = ?,
                    next_run_at = ?, version = version + 1
                WHERE id = ?
                """, request.name().trim(), request.contentType(), request.topic().trim(),
                request.scheduleType(), request.dayOfWeek(), Time.valueOf(request.publishTime()),
                request.publishMode(), text(request.preferredProvider()),
                request.imageEnabled(), request.enabled(), nextRunAt, id);
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbc.update("DELETE FROM ai_content_task WHERE id = ?", id);
        if (deleted == 0) throw new BusinessException(40401, "AI生成任务不存在");
    }

    @Transactional
    public List<Long> claimDueTasks() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, schedule_type AS scheduleType, day_of_week AS dayOfWeek,
                       publish_time AS publishTime, next_run_at AS nextRunAt
                FROM ai_content_task
                WHERE enabled = 1 AND content_type = 'card' AND publish_mode = 'auto'
                  AND next_run_at IS NOT NULL AND next_run_at <= NOW(3)
                ORDER BY next_run_at LIMIT 10
                """);
        java.util.ArrayList<Long> claimed = new java.util.ArrayList<>();
        for (Map<String, Object> row : due) {
            long id = ((Number) row.get("id")).longValue();
            LocalDateTime expected = toLocalDateTime(row.get("nextRunAt"));
            String schedule = String.valueOf(row.get("scheduleType"));
            int day = ((Number) row.get("dayOfWeek")).intValue();
            LocalTime time = toLocalTime(row.get("publishTime"));
            LocalDateTime next = nextRun(schedule, day, time, now().plusSeconds(1));
            int updated = jdbc.update("""
                    UPDATE ai_content_task
                    SET next_run_at = ?, last_run_at = NOW(3), last_result = 'running',
                        last_error = '', version = version + 1
                    WHERE id = ? AND next_run_at = ? AND enabled = 1
                    """, next, id, expected);
            if (updated == 1) claimed.add(id);
        }
        return claimed;
    }

    public void markResult(long id, String result, String error) {
        jdbc.update("""
                UPDATE ai_content_task
                SET last_result = ?, last_error = ?, version = version + 1
                WHERE id = ?
                """, result, truncate(error, 1000), id);
    }

    public LocalDateTime nextRun(ContentTaskRequest request, LocalDateTime from) {
        return nextRun(
                request.scheduleType(), request.dayOfWeek(), request.publishTime(), from);
    }

    static LocalDateTime nextRun(
            String scheduleType, int dayOfWeek, LocalTime time, LocalDateTime from) {
        if ("daily".equals(scheduleType)) {
            LocalDateTime candidate = LocalDateTime.of(from.toLocalDate(), time);
            return candidate.isAfter(from) ? candidate : candidate.plusDays(1);
        }
        LocalDate date = from.toLocalDate().with(
                TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
        LocalDateTime candidate = LocalDateTime.of(date, time);
        return candidate.isAfter(from) ? candidate : candidate.plusWeeks(1);
    }

    private void validate(ContentTaskRequest request) {
        if (!TYPES.contains(request.contentType())) {
            throw new BusinessException(40001, "AI生成任务仅支持科普卡片");
        }
        if (!SCHEDULES.contains(request.scheduleType())) {
            throw new BusinessException(40001, "生成周期仅支持每日或每周");
        }
        if (!MODES.contains(request.publishMode())) {
            throw new BusinessException(40001, "科普卡片由AI自动发布");
        }
    }

    private LocalTime toLocalTime(Object value) {
        if (value instanceof Time time) return time.toLocalTime();
        if (value instanceof LocalTime time) return time;
        return LocalTime.parse(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private String truncate(String value, int length) {
        String text = value == null ? "" : value;
        return text.length() <= length ? text : text.substring(0, length);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
