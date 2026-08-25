package io.healthresetplan.modules.ai.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.data.UserDataService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiHealthContextService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long DAY_MILLIS = 86_400_000L;

    private final UserDataService userDataService;
    private final ObjectMapper objectMapper;

    public AiHealthContextService(UserDataService userDataService, ObjectMapper objectMapper) {
        this.userDataService = userDataService;
        this.objectMapper = objectMapper;
    }

    public HealthContext build(String userId, boolean personalized) {
        if (!personalized) return HealthContext.empty();
        Map<String, Object> data = userDataService.load(userId).data();
        List<String> sections = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        appendProfile(rows(data, "user_profile"), sections, sources);
        appendMemories(rows(data, "ai_memory"), sections, sources);
        appendIndicators(rows(data, "health_indicator"), sections, sources);
        appendMeals(rows(data, "meal_record"), sections, sources);
        appendTodayPlans(rows(data, "plan"), sections, sources);
        appendClockRecords(rows(data, "clock_record"), sections, sources);
        appendReminders(rows(data, "reminder"), sections, sources);
        appendReports(rows(data, "health_report"), sections, sources);
        appendWeeklyReports(rows(data, "ai_weekly_report"), sections, sources);
        appendQuitSmoking(rows(data, "smoking_event"), sections, sources);

        if (sections.isEmpty()) return HealthContext.empty();
        return new HealthContext(String.join("\n", sections), List.copyOf(sources));
    }

    private void appendProfile(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.get(0);
        List<String> values = new ArrayList<>();
        int birthYear = intValue(row.get("birth_year"));
        if (birthYear > 1900) values.add("年龄=" + (LocalDate.now(ZONE).getYear() - birthYear));
        add(values, "性别", row.get("gender"));
        add(values, "身高cm", row.get("height_cm"));
        add(values, "体重kg", row.get("weight_kg"));
        add(values, "健康目标", row.get("goal"));
        add(values, "运动基础", row.get("exercise_base"));
        add(values, "饮食偏好", row.get("diet_preference"));
        add(values, "既往情况", row.get("medical_history"));
        add(values, "当前用药", row.get("medications"));
        if (!values.isEmpty()) {
            sections.add("【用户档案】" + String.join("；", values));
            sources.add("健康档案");
        }
    }

    private void appendMemories(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        List<String> memories = rows.stream()
                .filter(row -> intValue(row.getOrDefault("enabled", 1)) != 0)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("updated_at"))))
                .map(row -> limited(row.get("content"), 120))
                .filter(value -> !value.isBlank())
                .limit(12)
                .toList();
        if (!memories.isEmpty()) {
            sections.add("【用户确认的长期偏好】" + String.join("；", memories));
            sources.add("管家记忆");
        }
    }

    private void appendIndicators(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        long since = System.currentTimeMillis() - 30 * DAY_MILLIS;
        Map<String, Map<String, Object>> latest = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> longValue(row.get("measured_at")) >= since)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("measured_at"))))
                .forEach(row -> latest.putIfAbsent(String.valueOf(row.get("type")), row));
        List<String> values = latest.values().stream().limit(10).map(row -> {
            String type = limited(row.get("type"), 24);
            String date = date(longValue(row.get("measured_at")));
            Map<String, Object> payload = jsonMap(row.get("payload_json"));
            return type + "(" + date + ")=" + limited(payload, 180);
        }).toList();
        if (!values.isEmpty()) {
            sections.add("【近30天最新健康指标】" + String.join("；", values));
            sources.add("近30天健康指标");
        }
    }

    private void appendMeals(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        long since = System.currentTimeMillis() - 30 * DAY_MILLIS;
        List<Map<String, Object>> recent = rows.stream()
                .filter(row -> longValue(row.get("eaten_at")) >= since)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("eaten_at"))))
                .limit(18)
                .toList();
        if (recent.isEmpty()) return;
        double calories = recent.stream().mapToDouble(row -> doubleValue(row.get("total_calories"))).sum();
        List<String> meals = recent.stream().limit(10).map(row ->
                date(longValue(row.get("eaten_at"))) + " "
                        + limited(row.get("meal_type"), 16) + " "
                        + limited(row.get("name"), 40) + " "
                        + Math.round(doubleValue(row.get("total_calories"))) + "kcal"
        ).toList();
        sections.add("【近30天饮食】共" + recent.size() + "次，记录热量合计约" + Math.round(calories)
                + "kcal；最近记录=" + String.join("；", meals));
        sources.add("近30天饮食记录");
    }

    private void appendClockRecords(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        long since = System.currentTimeMillis() - 30 * DAY_MILLIS;
        List<String> records = rows.stream()
                .filter(row -> longValue(row.get("clock_at")) >= since)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("clock_at"))))
                .limit(24)
                .map(row -> date(longValue(row.get("clock_at"))) + " "
                        + limited(row.get("type"), 16) + " "
                        + limited(row.get("note"), 80) + " 状态=" + limited(row.get("status"), 16))
                .toList();
        if (!records.isEmpty()) {
            sections.add("【近30天健康打卡】" + String.join("；", records));
            sources.add("近30天健康打卡");
        }
    }

    private void appendReminders(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        List<String> reminders = rows.stream()
                .filter(row -> intValue(row.getOrDefault("is_enabled", 1)) != 0)
                .limit(16)
                .map(row -> limited(row.get("type"), 16) + "：" + limited(row.get("payload_json"), 120))
                .toList();
        if (!reminders.isEmpty()) {
            sections.add("【当前提醒】" + String.join("；", reminders));
            sources.add("当前健康提醒");
        }
    }

    private void appendReports(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        List<String> reports = rows.stream()
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("updated_at"))))
                .limit(8)
                .map(row -> date(longValue(row.get("report_time"))) + "：" + limited(row.get("summary"), 160))
                .filter(value -> !value.endsWith("："))
                .toList();
        if (!reports.isEmpty()) {
            sections.add("【健康报告】" + String.join("；", reports));
            sources.add("健康报告");
        }
    }

    private void appendWeeklyReports(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        List<String> reports = rows.stream()
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("created_at"))))
                .limit(4)
                .map(row -> date(longValue(row.get("start_at"))) + "-" + date(longValue(row.get("end_at")))
                        + "：" + limited(row.get("structured_json"), 180))
                .filter(value -> !value.isBlank())
                .toList();
        if (!reports.isEmpty()) {
            sections.add("【健康周报】" + String.join("；", reports));
            sources.add("健康周报");
        }
    }

    private void appendQuitSmoking(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        long since = System.currentTimeMillis() - 30 * DAY_MILLIS;
        List<String> events = rows.stream()
                .filter(row -> longValue(row.get("occurred_at")) >= since)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("occurred_at"))))
                .limit(20)
                .map(row -> date(longValue(row.get("occurred_at"))) + "：" + limited(row.get("note"), 100))
                .toList();
        if (!events.isEmpty()) {
            sections.add("【近30天戒烟记录】" + String.join("；", events));
            sources.add("近30天戒烟记录");
        }
    }

    private void appendTodayPlans(List<Map<String, Object>> rows, List<String> sections, List<String> sources) {
        LocalDate today = LocalDate.now(ZONE);
        List<String> plans = rows.stream()
                .filter(row -> today.equals(localDate(longValue(row.get("plan_date")))))
                .limit(8)
                .map(row -> limited(row.get("type"), 20) + "："
                        + limited(jsonMap(row.get("payload_json")).get("summary"), 120))
                .filter(value -> !value.endsWith("："))
                .toList();
        if (!plans.isEmpty()) {
            sections.add("【今日健康计划】" + String.join("；", plans));
            sources.add("今日健康计划");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private Map<String, Object> jsonMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (!(value instanceof String text) || text.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void add(List<String> values, String label, Object value) {
        String text = limited(value, 160);
        if (!text.isBlank() && !"0".equals(text) && !"unknown".equals(text)) {
            values.add(label + "=" + text);
        }
    }

    private String limited(Object value, int maxLength) {
        String text = value == null ? "" : String.valueOf(value).trim().replaceAll("[\\r\\n]+", " ");
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : parseLong(value).intValue();
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : parseLong(value);
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String date(long epochMillis) {
        LocalDate value = localDate(epochMillis);
        return value == null ? "日期未知" : value.toString();
    }

    private LocalDate localDate(long epochMillis) {
        if (epochMillis <= 0) return null;
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate();
    }

    public record HealthContext(String prompt, List<String> sources) {
        static HealthContext empty() {
            return new HealthContext("", List.of());
        }
    }
}
