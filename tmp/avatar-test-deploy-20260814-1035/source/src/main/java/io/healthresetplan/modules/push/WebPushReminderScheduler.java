package io.healthresetplan.modules.push;

import io.healthresetplan.config.WebPushProperties;
import io.healthresetplan.modules.data.UserDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class WebPushReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(WebPushReminderScheduler.class);
    private final WebPushProperties properties;
    private final WebPushSubscriptionService subscriptions;
    private final UserDataService userDataService;

    public WebPushReminderScheduler(
            WebPushProperties properties,
            WebPushSubscriptionService subscriptions,
            UserDataService userDataService) {
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.userDataService = userDataService;
    }

    @Scheduled(cron = "5,35 * * * * *")
    public synchronized void sendDueReminders() {
        if (!properties.isEnabled()) return;
        Map<String, Map<String, Object>> snapshots = new java.util.HashMap<>();
        for (var subscription : subscriptions.activeSubscriptions()) {
            try {
                ZoneId zone = ZoneId.of(subscription.timezone());
                ZonedDateTime now = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
                String minuteKey = now.toInstant().toString();
                if (subscriptions.alreadyDelivered(subscription.id(), minuteKey)) continue;
                Map<String, Object> data = snapshots.computeIfAbsent(
                        subscription.userId(), userId -> userDataService.load(userId).data());
                if (hasDueReminder(data.get("reminder"), now)) {
                    subscriptions.send(subscription, minuteKey);
                }
            } catch (Exception ex) {
                log.warn("Web Push 提醒发送失败: subscriptionId={}, error={}",
                        subscription.id(), ex.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanup() {
        subscriptions.cleanupDeliveries();
    }

    private boolean hasDueReminder(Object raw, ZonedDateTime now) {
        if (!(raw instanceof List<?> reminders)) return false;
        for (Object item : reminders) {
            if (item instanceof Map<?, ?> reminder && isDue(reminder, now)) return true;
        }
        return false;
    }

    boolean isDue(Map<?, ?> reminder, ZonedDateTime now) {
        if ("paused".equals(String.valueOf(reminder.get("status")))) return false;
        Object rawTime = reminder.get("remind_at");
        if (!(rawTime instanceof Number number)) return false;
        ZonedDateTime remindTime = Instant.ofEpochMilli(number.longValue()).atZone(now.getZone());
        if (remindTime.getHour() != now.getHour() || remindTime.getMinute() != now.getMinute()) return false;

        Map<?, ?> payload = parsePayload(reminder.get("payload_json"));
        boolean weekly = "local".equals(String.valueOf(reminder.get("channel")))
                && !"once".equals(String.valueOf(payload.get("scheduleMode")));
        if (!weekly) return remindTime.toLocalDate().equals(now.toLocalDate());

        LocalDate start = remindTime.toLocalDate();
        Object rawStart = payload.get("startDate");
        if (rawStart instanceof Number startNumber) {
            start = Instant.ofEpochMilli(startNumber.longValue()).atZone(now.getZone()).toLocalDate();
        }
        if (now.toLocalDate().isBefore(start)) return false;
        Object rawWeekdays = payload.get("weekdays");
        if (!(rawWeekdays instanceof List<?> weekdays) || weekdays.isEmpty()) return true;
        return weekdays.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .anyMatch(day -> day.intValue() == now.getDayOfWeek().getValue());
    }

    private Map<?, ?> parsePayload(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        if (!(value instanceof String json) || json.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
