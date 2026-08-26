package io.healthresetplan.modules.ai.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.data.UserDataService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        return build(userId, personalized, "");
    }

    public HealthContext build(String userId, boolean personalized, String question) {
        if (!personalized) return HealthContext.empty();
        Map<String, Object> data = userDataService.load(userId).data();
        List<String> sections = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        appendProfile(rows(data, "user_profile"), sections, sources);
        appendMemories(rows(data, "ai_memory"), sections, sources);
        appendIndicators(rows(data, "health_indicator"), question, sections, sources);
        appendMeals(rows(data, "meal_record"), question, sections, sources);
        appendTodayPlans(rows(data, "plan"), question, sections, sources);
        appendClockRecords(rows(data, "clock_record"), question, sections, sources);
        appendReminders(rows(data, "reminder"), sections, sources);
        appendReports(rows(data, "health_report"), question, sections, sources);
        appendWeeklyReports(rows(data, "ai_weekly_report"), sections, sources);
        appendQuitSmoking(rows(data, "smoking_event"), question, sections, sources);

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

    private void appendIndicators(List<Map<String, Object>> rows, String question,
                                  List<String> sections, List<String> sources) {
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

        DateRange requested = indicatorDateRange(question);
        if (requested == null || !asksForStatistics(question)) return;
        appendIndicatorStatistics(rows, requested, sections, sources);
    }

    private void appendIndicatorStatistics(List<Map<String, Object>> rows, DateRange requested,
                                           List<String> sections, List<String> sources) {
        List<Map<String, Object>> matched = rows.stream()
                .filter(row -> longValue(row.get("measured_at")) >= requested.startMillis
                        && longValue(row.get("measured_at")) < requested.endMillis)
                .toList();
        List<String> statistics = new ArrayList<>();
        for (String type : List.of("bp", "weight", "sleep")) {
            List<Map<String, Object>> typed = matched.stream()
                    .filter(row -> type.equals(String.valueOf(row.get("type"))))
                    .toList();
            if (typed.isEmpty()) continue;
            List<Double> values = typed.stream().map(row -> indicatorValue(row, type))
                    .filter(value -> value != null).toList();
            if (values.isEmpty()) continue;
            double first = values.get(values.size() - 1);
            double last = values.get(0);
            statistics.add(indicatorLabel(type) + "记录" + values.size() + "次，最低"
                    + formatNumber(values.stream().mapToDouble(Double::doubleValue).min().orElse(0))
                    + "，最高" + formatNumber(values.stream().mapToDouble(Double::doubleValue).max().orElse(0))
                    + "，平均" + formatNumber(values.stream().mapToDouble(Double::doubleValue).average().orElse(0))
                    + "，变化" + signed(formatNumber(last - first)));
        }
        if (!statistics.isEmpty()) {
            sections.add("【" + requested.label + "健康指标统计】" + String.join("；", statistics));
            sources.add(requested.label + "健康指标统计");
        } else {
            sections.add("【" + requested.label + "健康指标统计】记录不足，无法判断趋势");
            sources.add(requested.label + "健康指标统计");
        }
    }

    private Double indicatorValue(Map<String, Object> row, String type) {
        Map<String, Object> payload = jsonMap(row.get("payload_json"));
        return switch (type) {
            case "bp" -> doubleOrNull(payload.get("systolic"));
            case "weight" -> doubleOrNull(payload.get("weightKg"));
            case "sleep" -> doubleOrNull(payload.get("sleepHours"));
            default -> null;
        };
    }

    private String indicatorLabel(String type) {
        return switch (type) {
            case "bp" -> "收缩压";
            case "weight" -> "体重kg";
            case "sleep" -> "睡眠小时";
            default -> type;
        };
    }

    private boolean asksForStatistics(String question) {
        return containsAny(question == null ? "" : question, "最高", "最低", "平均", "变化", "趋势", "对比");
    }

    private DateRange indicatorDateRange(String question) {
        String text = question == null ? "" : question.trim();
        if (text.isBlank() || !containsAny(text, "血压", "体重", "睡眠", "指标", "健康数据")) return null;
        LocalDate today = LocalDate.now(ZONE);
        if (containsAny(text, "上周", "上一周")) {
            LocalDate start = today.minusDays(today.getDayOfWeek().getValue() + 6L);
            return range(start, start.plusDays(7), "上周");
        }
        if (containsAny(text, "本周", "这周", "这一周")) {
            return range(today.minusDays(today.getDayOfWeek().getValue() - 1L), today.plusDays(1), "本周");
        }
        if (containsAny(text, "最近30天", "近30天", "过去30天")) {
            return range(today.minusDays(29), today.plusDays(1), "最近30天");
        }
        if (containsAny(text, "昨天", "昨日")) return dayRange(today.minusDays(1), "昨天");
        if (containsAny(text, "今天", "今日")) return dayRange(today, "今天");
        return null;
    }

    private void appendMeals(List<Map<String, Object>> rows, String question,
                             List<String> sections, List<String> sources) {
        DateRange requested = mealDateRange(question);
        long since = requested == null ? System.currentTimeMillis() - 30 * DAY_MILLIS : requested.startMillis;
        long until = requested == null ? Long.MAX_VALUE : requested.endMillis;
        List<Map<String, Object>> recent = rows.stream()
                .filter(row -> longValue(row.get("eaten_at")) >= since
                        && longValue(row.get("eaten_at")) < until)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("eaten_at"))))
                .limit(requested == null ? 18 : 30)
                .toList();
        if (recent.isEmpty()) {
            if (requested != null) {
                sections.add("【" + requested.label + "饮食】没有找到已保存的饮食记录");
                sources.add(requested.label + "饮食记录");
            }
            return;
        }
        double calories = recent.stream().mapToDouble(row -> doubleValue(row.get("total_calories"))).sum();
        List<String> meals = recent.stream().limit(requested == null ? 10 : 30)
                .map(this::mealSummary).toList();
        String label = requested == null ? "近30天" : requested.label;
        sections.add("【" + label + "饮食】共" + recent.size() + "次，记录热量合计约" + Math.round(calories)
                + "kcal；记录=" + String.join("；", meals));
        sources.add(label + "饮食记录");
    }

    private String mealSummary(Map<String, Object> row) {
        StringBuilder text = new StringBuilder(dateTime(longValue(row.get("eaten_at"))))
                .append(" ").append(mealType(row.get("meal_type")))
                .append(" ").append(limited(row.get("name"), 40))
                .append(" ").append(Math.round(doubleValue(row.get("total_calories")))).append("kcal");
        List<String> foods = jsonList(row.get("foods_json")).stream()
                .map(food -> limited(food.get("name"), 30))
                .filter(value -> !value.isBlank()).limit(12).toList();
        if (!foods.isEmpty()) text.append("，食物=").append(String.join("、", foods));
        String note = limited(row.get("note"), 80);
        if (!note.isBlank()) text.append("，备注=").append(note);
        return text.toString();
    }

    private DateRange mealDateRange(String question) {
        String text = question == null ? "" : question.trim();
        if (text.isBlank() || !containsAny(text, "吃", "饮食", "早餐", "午餐", "晚餐", "加餐", "夜宵", "餐食")) return null;
        LocalDate today = LocalDate.now(ZONE);
        if (containsAny(text, "前天")) return dayRange(today.minusDays(2), "前天");
        if (containsAny(text, "昨天", "昨日")) return dayRange(today.minusDays(1), "昨天");
        if (containsAny(text, "今天", "今日")) return dayRange(today, "今天");
        if (containsAny(text, "近7天", "最近7天", "最近一周", "过去一周")) {
            return range(today.minusDays(6), today.plusDays(1), "近7天");
        }
        return null;
    }

    private DateRange dayRange(LocalDate date, String label) { return range(date, date.plusDays(1), label); }

    private DateRange range(LocalDate start, LocalDate end, String label) {
        return new DateRange(start.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                end.atStartOfDay(ZONE).toInstant().toEpochMilli(), label);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private void appendClockRecords(List<Map<String, Object>> rows, String question,
                                    List<String> sections, List<String> sources) {
        DateRange requested = generalDateRange(question);
        long since = requested == null ? System.currentTimeMillis() - 30 * DAY_MILLIS : requested.startMillis;
        long until = requested == null ? Long.MAX_VALUE : requested.endMillis;
        List<String> records = rows.stream()
                .filter(row -> longValue(row.get("clock_at")) >= since && longValue(row.get("clock_at")) < until)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("clock_at"))))
                .limit(24)
                .map(row -> date(longValue(row.get("clock_at"))) + " "
                        + limited(row.get("type"), 16) + " "
                        + limited(row.get("note"), 80) + " 状态=" + limited(row.get("status"), 16))
                .toList();
        if (!records.isEmpty()) {
            String label = requested == null ? "近30天" : requested.label;
            sections.add("【" + label + "健康打卡】" + String.join("；", records));
            sources.add(label + "健康打卡");
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

    private void appendReports(List<Map<String, Object>> rows, String question,
                               List<String> sections, List<String> sources) {
        DateRange requested = generalDateRange(question);
        List<String> reports = rows.stream()
                .filter(row -> requested == null
                        || (longValue(row.get("report_time")) >= requested.startMillis
                        && longValue(row.get("report_time")) < requested.endMillis))
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("updated_at"))))
                .limit(8)
                .map(row -> date(longValue(row.get("report_time"))) + "：" + limited(row.get("summary"), 160))
                .filter(value -> !value.endsWith("："))
                .toList();
        if (!reports.isEmpty()) {
            String label = requested == null ? "健康报告" : requested.label + "健康报告";
            sections.add("【" + label + "】" + String.join("；", reports));
            sources.add(label);
        } else if (requested != null && containsAny(question, "报告")) {
            sections.add("【" + requested.label + "健康报告】没有找到已保存的报告");
            sources.add(requested.label + "健康报告");
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

    private void appendQuitSmoking(List<Map<String, Object>> rows, String question,
                                   List<String> sections, List<String> sources) {
        DateRange requested = generalDateRange(question);
        long since = requested == null ? System.currentTimeMillis() - 30 * DAY_MILLIS : requested.startMillis;
        long until = requested == null ? Long.MAX_VALUE : requested.endMillis;
        List<String> events = rows.stream()
                .filter(row -> longValue(row.get("occurred_at")) >= since && longValue(row.get("occurred_at")) < until)
                .sorted(Comparator.comparingLong(row -> -longValue(row.get("occurred_at"))))
                .limit(20)
                .map(row -> date(longValue(row.get("occurred_at"))) + "：" + limited(row.get("note"), 100))
                .toList();
        if (!events.isEmpty()) {
            String label = requested == null ? "近30天" : requested.label;
            sections.add("【" + label + "戒烟记录】" + String.join("；", events));
            sources.add(label + "戒烟记录");
        }
    }

    private void appendTodayPlans(List<Map<String, Object>> rows, String question,
                                  List<String> sections, List<String> sources) {
        LocalDate today = LocalDate.now(ZONE);
        DateRange requested = generalDateRange(question);
        LocalDate start = requested == null ? today : localDate(requested.startMillis);
        LocalDate end = requested == null ? today.plusDays(1) : localDate(requested.endMillis);
        List<String> plans = rows.stream()
                .filter(row -> {
                    LocalDate date = localDate(longValue(row.get("plan_date")));
                    return date != null && !date.isBefore(start) && date.isBefore(end);
                })
                .limit(8)
                .map(row -> limited(row.get("type"), 20) + "："
                        + limited(jsonMap(row.get("payload_json")).get("summary"), 120))
                .filter(value -> !value.endsWith("："))
                .toList();
        if (!plans.isEmpty()) {
            String label = requested == null ? "今日" : requested.label;
            sections.add("【" + label + "健康计划】" + String.join("；", plans));
            sources.add(label + "健康计划");
        }
    }

    private DateRange generalDateRange(String question) {
        String text = question == null ? "" : question;
        LocalDate today = LocalDate.now(ZONE);
        if (containsAny(text, "上周", "上一周")) {
            LocalDate start = today.minusDays(today.getDayOfWeek().getValue() + 6L);
            return range(start, start.plusDays(7), "上周");
        }
        if (containsAny(text, "本周", "这周", "这一周"))
            return range(today.minusDays(today.getDayOfWeek().getValue() - 1L), today.plusDays(1), "本周");
        if (containsAny(text, "最近30天", "近30天", "过去30天"))
            return range(today.minusDays(29), today.plusDays(1), "最近30天");
        if (containsAny(text, "昨天", "昨日")) return dayRange(today.minusDays(1), "昨天");
        if (containsAny(text, "今天", "今日")) return dayRange(today, "今天");
        return null;
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

    private List<Map<String, Object>> jsonList(Object value) {
        Object parsed = value;
        if (value instanceof String text && !text.isBlank()) {
            try {
                parsed = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (!(parsed instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) item).forEach((key, field) -> result.put(String.valueOf(key), field));
            return result;
        }).toList();
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

    private Double doubleOrNull(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.01
                ? String.valueOf(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String signed(String value) {
        return value.startsWith("-") || "0".equals(value) ? value : "+" + value;
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

    private String dateTime(long epochMillis) {
        if (epochMillis <= 0) return "日期未知";
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String mealType(Object value) {
        return switch (String.valueOf(value)) {
            case "breakfast" -> "早餐";
            case "dinner" -> "晚餐";
            case "snack" -> "加餐";
            case "late_night" -> "夜宵";
            default -> "午餐";
        };
    }

    public record HealthContext(String prompt, List<String> sources) {
        static HealthContext empty() {
            return new HealthContext("", List.of());
        }
    }

    private record DateRange(long startMillis, long endMillis, String label) {}
}
