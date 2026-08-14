package io.healthresetplan.modules.push;

import io.healthresetplan.config.WebPushProperties;
import io.healthresetplan.modules.data.UserDataService;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebPushReminderSchedulerTests {
    private final WebPushReminderScheduler scheduler = new WebPushReminderScheduler(
            new WebPushProperties(), mock(WebPushSubscriptionService.class), mock(UserDataService.class));
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");

    @Test
    void oneTimeReminderOnlyMatchesItsDateAndMinute() {
        ZonedDateTime due = ZonedDateTime.of(2026, 8, 5, 9, 30, 0, 0, zone);
        Map<String, Object> reminder = Map.of(
                "status", "pending",
                "remind_at", due.toInstant().toEpochMilli(),
                "channel", "plan",
                "payload_json", "{}");

        assertTrue(scheduler.isDue(reminder, due));
        assertFalse(scheduler.isDue(reminder, due.plusDays(1)));
        assertFalse(scheduler.isDue(reminder, due.plusMinutes(1)));
    }

    @Test
    void weeklyReminderMatchesSelectedWeekdayAfterStartDate() {
        ZonedDateTime monday = ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone);
        ZonedDateTime start = monday.minusDays(3);
        Map<String, Object> reminder = Map.of(
                "status", "pending",
                "remind_at", start.toInstant().toEpochMilli(),
                "channel", "local",
                "payload_json", Map.of(
                        "scheduleMode", "weekly",
                        "startDate", start.toInstant().toEpochMilli(),
                        "weekdays", List.of(1, 3)));

        assertTrue(scheduler.isDue(reminder, monday));
        assertFalse(scheduler.isDue(reminder, monday.plusDays(1)));
    }
}
