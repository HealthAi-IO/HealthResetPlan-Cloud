package io.healthresetplan.modules.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.modules.sync.entity.HealthIndicator;
import io.healthresetplan.modules.sync.mapper.HealthIndicatorMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 端到端加密同步服务。
 *
 * <p>push 实现"幂等 upsert + 客户端版本胜出"：
 * 若服务端已有该 client_id 且版本更高，则服务端不覆盖（客户端下次 pull 后会更新本地）。</p>
 *
 * <p>pull 实现增量拉取：按 server_updated_at > since 过滤，升序返回，客户端再次用该批
 * 最大的 serverTime 作为下次 since。</p>
 */
@Service
public class BackendSyncService {

    private static final Set<String> ALLOWED_TABLES = Set.of("health_indicator");

    private final HealthIndicatorMapper healthIndicatorMapper;

    public BackendSyncService(HealthIndicatorMapper healthIndicatorMapper) {
        this.healthIndicatorMapper = healthIndicatorMapper;
    }

    public int push(String userId, String deviceId, List<SyncPushRequest.Item> items) {
        if (items == null || items.isEmpty()) return 0;
        int accepted = 0;
        for (var item : items) {
            if (!ALLOWED_TABLES.contains(item.table())) continue;
            if ("health_indicator".equals(item.table())) {
                pushHealthIndicator(userId, deviceId != null ? deviceId : "", item);
                accepted++;
            }
        }
        return accepted;
    }

    private void pushHealthIndicator(String userId, String deviceId, SyncPushRequest.Item item) {
        var existing = healthIndicatorMapper.selectOne(
                new LambdaQueryWrapper<HealthIndicator>()
                        .eq(HealthIndicator::getUserId, userId)
                        .eq(HealthIndicator::getClientId, item.clientId())
        );

        var now = LocalDateTime.now();
        var meta = item.meta() != null ? item.meta() : Map.of();
        var type = String.valueOf(meta.getOrDefault("type", ""));
        var source = String.valueOf(meta.getOrDefault("source", "manual"));
        var measuredAt = meta.containsKey("measured_at")
                ? fromEpochMilli(((Number) meta.get("measured_at")).longValue())
                : now;
        var clientUpdatedAt = item.clientUpdatedAt() > 0
                ? fromEpochMilli(item.clientUpdatedAt())
                : now;
        var algStr = item.alg() != null ? item.alg() : "aes-256-gcm:v1";

        if (existing == null) {
            var ind = new HealthIndicator();
            ind.setUserId(userId);
            ind.setClientId(item.clientId());
            ind.setType(type);
            ind.setPayloadCipher(item.cipher());
            ind.setPayloadIv(item.iv());
            ind.setPayloadTag(item.tag());
            ind.setAlg(algStr);
            ind.setSource(source);
            ind.setMeasuredAt(measuredAt);
            ind.setDeviceId(deviceId);
            ind.setClientUpdatedAt(clientUpdatedAt);
            ind.setServerUpdatedAt(now);
            ind.setCreatedAt(now);
            ind.setUpdatedAt(now);
            ind.setVersion(0L);
            healthIndicatorMapper.insert(ind);
        } else {
            long serverVersion = existing.getVersion() != null ? existing.getVersion() : 0L;
            if (item.version() > serverVersion) {
                existing.setPayloadCipher(item.cipher());
                existing.setPayloadIv(item.iv());
                existing.setPayloadTag(item.tag());
                existing.setAlg(algStr);
                existing.setType(type);
                existing.setSource(source);
                existing.setMeasuredAt(measuredAt);
                existing.setDeviceId(deviceId);
                existing.setClientUpdatedAt(clientUpdatedAt);
                existing.setServerUpdatedAt(now);
                existing.setVersion(item.version());
                healthIndicatorMapper.updateById(existing);
            }
            // server version >= client version → skip；客户端下次 pull 时会同步到最新
        }
    }

    public List<SyncPullItem> pull(String userId, long sinceMs, int limit) {
        var since = sinceMs > 0
                ? fromEpochMilli(sinceMs)
                : LocalDateTime.of(2000, 1, 1, 0, 0);

        return healthIndicatorMapper
                .selectByUserSince(userId, since, Math.min(limit, 500))
                .stream()
                .map(ind -> new SyncPullItem(
                        "health_indicator",
                        ind.getClientId(),
                        ind.getVersion() != null ? ind.getVersion() : 0L,
                        ind.getClientUpdatedAt() != null
                                ? toEpochMilli(ind.getClientUpdatedAt())
                                : 0L,
                        ind.getPayloadCipher(),
                        ind.getPayloadIv(),
                        ind.getPayloadTag(),
                        ind.getAlg() != null ? ind.getAlg() : "aes-256-gcm:v1",
                        Map.of(
                                "type", ind.getType() != null ? ind.getType() : "",
                                "measured_at", ind.getMeasuredAt() != null
                                        ? toEpochMilli(ind.getMeasuredAt())
                                        : 0L,
                                "source", ind.getSource() != null ? ind.getSource() : "manual"
                        )
                ))
                .toList();
    }

    private static LocalDateTime fromEpochMilli(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static long toEpochMilli(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
