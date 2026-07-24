package io.healthresetplan.modules.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.sync.entity.SyncRecord;
import io.healthresetplan.modules.sync.mapper.HealthIndicatorMapper;
import io.healthresetplan.modules.sync.mapper.SyncRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendSyncServiceTests {

    @Test
    void newerDeviceWinsEqualTimestampAndTombstoneRemainsPermanent() {
        var records = mock(SyncRecordMapper.class);
        var indicators = mock(HealthIndicatorMapper.class);
        var retention = mock(KeyRetentionService.class);
        var service = new BackendSyncService(records, indicators, retention, new ObjectMapper());
        var existing = record("device-b", 1_700_000_000_000L);
        when(records.selectOneIncludingDeleted(anyString(), anyString(), anyString()))
                .thenReturn(null, existing, existing, existing, existing);

        var first = item("row-1", 1_700_000_000_000L, false);
        assertThat(service.push("user-1", "device-b", "finger", List.of(first)).accepted())
                .isEqualTo(1);

        var lowerDevice = item("row-1", 1_700_000_000_000L, false);
        assertThat(service.push("user-1", "device-a", "finger", List.of(lowerDevice)).accepted())
                .isEqualTo(0);

        var higherDevice = item("row-1", 1_700_000_000_000L, false);
        assertThat(service.push("user-1", "device-c", "finger", List.of(higherDevice)).accepted())
                .isEqualTo(1);
        assertThat(existing.getDeviceId()).isEqualTo("device-c");

        var deletion = item("row-1", 1_700_000_001_000L, true);
        assertThat(service.push("user-1", "device-a", "finger", List.of(deletion)).accepted())
                .isEqualTo(1);
        assertThat(existing.getDeletedAt()).isNotNull();

        var staleUpsert = item("row-1", 1_700_000_000_500L, false);
        assertThat(service.push("user-1", "device-z", "finger", List.of(staleUpsert)).accepted())
                .isEqualTo(0);
        assertThat(existing.getDeletedAt()).isNotNull();
    }

    @Test
    void pullUsesBoundedPagesAndReportsHasMore() {
        var records = mock(SyncRecordMapper.class);
        var indicators = mock(HealthIndicatorMapper.class);
        var retention = mock(KeyRetentionService.class);
        var service = new BackendSyncService(records, indicators, retention, new ObjectMapper());
        var rows = List.of(record("a", 1_700_000_000_000L), record("b", 1_700_000_001_000L),
                record("c", 1_700_000_002_000L));
        when(records.selectByUserBetweenAndKey(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(rows);

        var page = service.pull("user-1", "finger", 0, 1_800_000_000_000L, 0, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    private static SyncPushRequest.Item item(String id, long updatedAt, boolean deleted) {
        return new SyncPushRequest.Item(
                "plan", id, 1, updatedAt, deleted ? "" : "cipher", deleted ? "" : "iv",
                deleted ? "" : "tag", "aes-256-gcm:v1", deleted, false, Map.of());
    }

    private static SyncRecord record(String deviceId, long updatedAt) {
        var record = new SyncRecord();
        record.setUserId("user-1");
        record.setTableName("plan");
        record.setClientId("row-1");
        record.setDeviceId(deviceId);
        record.setKeyFingerprint("finger");
        record.setClientUpdatedAt(
                Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        record.setServerUpdatedAt(LocalDateTime.now());
        record.setVersion(1L);
        return record;
    }
}
