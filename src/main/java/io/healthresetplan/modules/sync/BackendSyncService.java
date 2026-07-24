package io.healthresetplan.modules.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
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
import java.util.Objects;
import java.util.Set;

@Service
public class BackendSyncService {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "user_profile",
            "health_indicator",
            "plan",
            "clock_record",
            "reminder",
            "health_report",
            "meal_record",
            "ai_session",
            "ai_message"
    );

    private final SyncRecordMapper syncRecordMapper;
    private final HealthIndicatorMapper healthIndicatorMapper;
    private final KeyRetentionService keyRetentionService;
    private final ObjectMapper objectMapper;

    public BackendSyncService(
            SyncRecordMapper syncRecordMapper,
            HealthIndicatorMapper healthIndicatorMapper,
            KeyRetentionService keyRetentionService,
            ObjectMapper objectMapper) {
        this.syncRecordMapper = syncRecordMapper;
        this.healthIndicatorMapper = healthIndicatorMapper;
        this.keyRetentionService = keyRetentionService;
        this.objectMapper = objectMapper;
    }

    public PushResult push(String userId, String deviceId, String keyFingerprint, List<SyncPushRequest.Item> items) {
        if (items == null || items.isEmpty()) return new PushResult(0, List.of());
        keyRetentionService.markUsed(userId, normalizeFingerprint(keyFingerprint));
        int accepted = 0;
        var rejected = new ArrayList<SyncRef>();
        for (var item : items) {
            validateItem(item);
            if (pushRecord(userId, deviceId != null ? deviceId : "", normalizeFingerprint(keyFingerprint), item)) {
                accepted++;
            } else {
                rejected.add(new SyncRef(item.table(), item.clientId()));
            }
        }
        return new PushResult(accepted, rejected);
    }

    private void validateItem(SyncPushRequest.Item item) {
        if (item == null) {
            throw new BusinessException(40002, "同步数据为空");
        }
        if (!ALLOWED_TABLES.contains(item.table())) {
            throw new BusinessException(40002, "不支持同步的数据类型：" + item.table());
        }
        if (item.clientId() == null || item.clientId().isBlank()) {
            throw new BusinessException(40002, "同步数据缺少客户端 ID");
        }
        if (Boolean.TRUE.equals(item.deleted())) {
            return;
        }
        if (item.cipher() == null || item.cipher().isBlank()
                || item.iv() == null || item.iv().isBlank()
                || item.tag() == null || item.tag().isBlank()) {
            throw new BusinessException(40002, "同步密文格式不完整");
        }
    }

    private boolean pushRecord(String userId, String deviceId, String keyFingerprint, SyncPushRequest.Item item) {
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
            record.setKeyFingerprint(keyFingerprint);
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
            return true;
        }

        var serverUpdatedAt = existing.getClientUpdatedAt();
        boolean keyChanged = !Objects.equals(existing.getKeyFingerprint(), keyFingerprint);
        boolean clientIsNewer = serverUpdatedAt == null || clientUpdatedAt.isAfter(serverUpdatedAt);
        boolean sameUpdateTime = serverUpdatedAt != null && clientUpdatedAt.isEqual(serverUpdatedAt);
        boolean deviceWinsTie = sameUpdateTime && deviceId.compareTo(
                existing.getDeviceId() != null ? existing.getDeviceId() : "") > 0;
        boolean isKeyMigration = Boolean.TRUE.equals(item.keyMigration());
        if ((isKeyMigration && keyChanged) || clientIsNewer || deviceWinsTie) {
            existing.setKeyFingerprint(keyFingerprint);
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
            return true;
        }
        return false;
    }

    public PullPage pull(String userId, String keyFingerprint, long sinceMs, long untilMs, int offset, int limit) {
        var normalizedFingerprint = normalizeFingerprint(keyFingerprint);
        keyRetentionService.markUsed(userId, normalizedFingerprint);
        var since = sinceMs > 0
                ? fromEpochMilli(sinceMs)
                : LocalDateTime.of(2000, 1, 1, 0, 0);
        var until = fromEpochMilli(untilMs);

        var cappedLimit = Math.min(limit, 500);
        var safeOffset = Math.max(offset, 0);
        var items = new ArrayList<SyncPullItem>();

        syncRecordMapper
                .selectByUserBetweenAndKey(userId, normalizedFingerprint, since, until, safeOffset, cappedLimit + 1)
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

        if (normalizedFingerprint.isBlank()) {
            healthIndicatorMapper
                    .selectByUserBetween(userId, since, until, cappedLimit + 1)
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
        }

        var ordered = items.stream()
                .sorted(Comparator.comparingLong(SyncPullItem::clientUpdatedAt))
                .toList();
        if (ordered.isEmpty()) return new PullPage(List.of(), false);
        int end = Math.min(cappedLimit, ordered.size());
        return new PullPage(ordered.subList(0, end), ordered.size() > cappedLimit);
    }

    public record PullPage(List<SyncPullItem> items, boolean hasMore) {}

    public record PushResult(int accepted, List<SyncRef> rejected) {}

    public record SyncRef(String table, String clientId) {}

    private String normalizeFingerprint(String keyFingerprint) {
        return keyFingerprint == null ? "" : keyFingerprint.trim();
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
