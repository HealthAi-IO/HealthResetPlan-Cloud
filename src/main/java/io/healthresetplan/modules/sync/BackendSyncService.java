package io.healthresetplan.modules.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.sync.entity.HealthIndicator;
import io.healthresetplan.modules.sync.entity.SyncRecord;
import io.healthresetplan.modules.sync.mapper.HealthIndicatorMapper;
import io.healthresetplan.modules.sync.mapper.SyncRecordMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BackendSyncService {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "user_profile",
            "health_indicator",
            "plan",
            "clock_record",
            "reminder",
            "health_report"
    );

    private final SyncRecordMapper syncRecordMapper;
    private final HealthIndicatorMapper healthIndicatorMapper;
    private final ObjectMapper objectMapper;

    public BackendSyncService(
            SyncRecordMapper syncRecordMapper,
            HealthIndicatorMapper healthIndicatorMapper,
            ObjectMapper objectMapper) {
        this.syncRecordMapper = syncRecordMapper;
        this.healthIndicatorMapper = healthIndicatorMapper;
        this.objectMapper = objectMapper;
    }

    public int push(String userId, String deviceId, List<SyncPushRequest.Item> items) {
        if (items == null || items.isEmpty()) return 0;
        int accepted = 0;
        for (var item : items) {
            if (!ALLOWED_TABLES.contains(item.table())) continue;
            pushRecord(userId, deviceId != null ? deviceId : "", item);
            accepted++;
        }
        return accepted;
    }

    private void pushRecord(String userId, String deviceId, SyncPushRequest.Item item) {
        var existing = syncRecordMapper.selectOneIncludingDeleted(userId, item.table(), item.clientId());

        var now = LocalDateTime.now();
        var clientUpdatedAt = item.clientUpdatedAt() > 0
                ? fromEpochMilli(item.clientUpdatedAt())
                : now;
        var version = Math.max(item.version(), 0L);
        var deleted = Boolean.TRUE.equals(item.deleted());

        if (existing == null) {
            var record = new SyncRecord();
            record.setUserId(userId);
            record.setTableName(item.table());
            record.setClientId(item.clientId());
            record.setPayloadCipher(item.cipher());
            record.setPayloadIv(item.iv());
            record.setPayloadTag(item.tag());
            record.setAlg(item.alg() != null ? item.alg() : "aes-256-gcm:v1");
            record.setMetaJson(writeMeta(item.meta()));
            record.setDeviceId(deviceId);
            record.setClientUpdatedAt(clientUpdatedAt);
            record.setServerUpdatedAt(now);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setDeletedAt(deleted ? now : null);
            record.setVersion(version);
            syncRecordMapper.insert(record);
            return;
        }

        long serverVersion = existing.getVersion() != null ? existing.getVersion() : 0L;
        if (version >= serverVersion) {
            existing.setPayloadCipher(item.cipher());
            existing.setPayloadIv(item.iv());
            existing.setPayloadTag(item.tag());
            existing.setAlg(item.alg() != null ? item.alg() : "aes-256-gcm:v1");
            existing.setMetaJson(writeMeta(item.meta()));
            existing.setDeviceId(deviceId);
            existing.setClientUpdatedAt(clientUpdatedAt);
            existing.setServerUpdatedAt(now);
            existing.setUpdatedAt(now);
            existing.setDeletedAt(deleted ? now : null);
            existing.setVersion(version);
            syncRecordMapper.updateById(existing);
        }
    }

    public List<SyncPullItem> pull(String userId, long sinceMs, int limit) {
        var since = sinceMs > 0
                ? fromEpochMilli(sinceMs)
                : LocalDateTime.of(2000, 1, 1, 0, 0);

        var cappedLimit = Math.min(limit, 500);
        var items = new ArrayList<SyncPullItem>();

        syncRecordMapper
                .selectByUserSince(userId, since, cappedLimit)
                .stream()
                .map(record -> new SyncPullItem(
                        record.getTableName(),
                        record.getClientId(),
                        record.getVersion() != null ? record.getVersion() : 0L,
                        record.getClientUpdatedAt() != null
                                ? toEpochMilli(record.getClientUpdatedAt())
                                : 0L,
                        record.getPayloadCipher(),
                        record.getPayloadIv(),
                        record.getPayloadTag(),
                        record.getAlg() != null ? record.getAlg() : "aes-256-gcm:v1",
                        record.getDeletedAt() != null,
                        readMeta(record.getMetaJson())
                ))
                .forEach(items::add);

        // Compatibility for data written before the generic sync_record table:
        // old health_indicator rows encrypted only payload_json. New clients
        // detect that format and rebuild a local health_indicator row.
        healthIndicatorMapper
                .selectByUserSince(userId, since, cappedLimit)
                .stream()
                .map(record -> new SyncPullItem(
                        "health_indicator",
                        record.getClientId(),
                        record.getVersion() != null ? record.getVersion() : 0L,
                        record.getClientUpdatedAt() != null
                                ? toEpochMilli(record.getClientUpdatedAt())
                                : 0L,
                        record.getPayloadCipher(),
                        record.getPayloadIv(),
                        record.getPayloadTag(),
                        record.getAlg() != null ? record.getAlg() : "aes-256-gcm:v1",
                        false,
                        healthIndicatorMeta(record)
                ))
                .forEach(items::add);

        return items.stream()
                .sorted(Comparator.comparingLong(SyncPullItem::clientUpdatedAt))
                .limit(cappedLimit)
                .toList();
    }

    private String writeMeta(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta != null ? meta : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(metaJson, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static LocalDateTime fromEpochMilli(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static long toEpochMilli(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Map<String, Object> healthIndicatorMeta(HealthIndicator record) {
        return Map.of(
                "type", record.getType() != null ? record.getType() : "weight",
                "source", record.getSource() != null ? record.getSource() : "cloud",
                "measured_at", record.getMeasuredAt() != null
                        ? toEpochMilli(record.getMeasuredAt())
                        : 0L
        );
    }
}
