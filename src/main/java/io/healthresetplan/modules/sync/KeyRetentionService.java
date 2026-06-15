package io.healthresetplan.modules.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.healthresetplan.modules.sync.entity.HealthIndicator;
import io.healthresetplan.modules.sync.entity.SyncRecord;
import io.healthresetplan.modules.sync.mapper.HealthIndicatorMapper;
import io.healthresetplan.modules.sync.mapper.SyncRecordMapper;
import io.healthresetplan.modules.user.entity.UserKeyMeta;
import io.healthresetplan.modules.user.mapper.UserKeyMetaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KeyRetentionService {

    private static final Logger log = LoggerFactory.getLogger(KeyRetentionService.class);
    private static final int RETENTION_DAYS = 90;

    private final UserKeyMetaMapper keyMetaMapper;
    private final SyncRecordMapper syncRecordMapper;
    private final HealthIndicatorMapper healthIndicatorMapper;

    public KeyRetentionService(
            UserKeyMetaMapper keyMetaMapper,
            SyncRecordMapper syncRecordMapper,
            HealthIndicatorMapper healthIndicatorMapper) {
        this.keyMetaMapper = keyMetaMapper;
        this.syncRecordMapper = syncRecordMapper;
        this.healthIndicatorMapper = healthIndicatorMapper;
    }

    @Transactional
    public void markUsed(String userId, String keyFingerprint) {
        if (userId == null || userId.isBlank() || keyFingerprint == null || keyFingerprint.isBlank()) {
            return;
        }

        var now = LocalDateTime.now();
        claimLegacyRows(userId, keyFingerprint, now);

        var existing = keyMetaMapper.selectOne(new LambdaQueryWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getUserId, userId)
                .eq(UserKeyMeta::getPublicFinger, keyFingerprint)
                .last("LIMIT 1"));

        if (existing == null) {
            var legacyMeta = keyMetaMapper.selectOne(new LambdaQueryWrapper<UserKeyMeta>()
                    .eq(UserKeyMeta::getUserId, userId)
                    .eq(UserKeyMeta::getPublicFinger, "")
                    .last("LIMIT 1"));
            if (legacyMeta != null) {
                legacyMeta.setPublicFinger(keyFingerprint);
                legacyMeta.setBackupMethod("mnemonic");
                legacyMeta.setBackedUp(1);
                if (legacyMeta.getBackedUpAt() == null) {
                    legacyMeta.setBackedUpAt(now);
                }
                legacyMeta.setLastUsedAt(now);
                legacyMeta.setRetentionStartedAt(null);
                legacyMeta.setRetentionUntil(null);
                legacyMeta.setPurgeStatus("active");
                legacyMeta.setPurgedAt(null);
                legacyMeta.setUpdatedAt(now);
                keyMetaMapper.updateById(legacyMeta);
                return;
            }

            var meta = new UserKeyMeta();
            meta.setUserId(userId);
            meta.setPublicFinger(keyFingerprint);
            meta.setBackupMethod("mnemonic");
            meta.setBackedUp(1);
            meta.setBackedUpAt(now);
            meta.setLastUsedAt(now);
            meta.setPurgeStatus("active");
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            keyMetaMapper.insert(meta);
            return;
        }

        existing.setLastUsedAt(now);
        existing.setRetentionStartedAt(null);
        existing.setRetentionUntil(null);
        existing.setPurgeStatus("active");
        existing.setPurgedAt(null);
        existing.setUpdatedAt(now);
        keyMetaMapper.updateById(existing);
    }

    private void claimLegacyRows(String userId, String keyFingerprint, LocalDateTime now) {
        syncRecordMapper.update(null, new LambdaUpdateWrapper<SyncRecord>()
                .eq(SyncRecord::getUserId, userId)
                .eq(SyncRecord::getKeyFingerprint, "")
                .set(SyncRecord::getKeyFingerprint, keyFingerprint)
                .set(SyncRecord::getUpdatedAt, now));

        keyMetaMapper.update(null, new LambdaUpdateWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getUserId, userId)
                .eq(UserKeyMeta::getPublicFinger, "")
                .set(UserKeyMeta::getLastUsedAt, now)
                .set(UserKeyMeta::getRetentionStartedAt, null)
                .set(UserKeyMeta::getRetentionUntil, null)
                .set(UserKeyMeta::getPurgeStatus, "active")
                .set(UserKeyMeta::getUpdatedAt, now));
    }

    @Transactional
    public void startRetentionForAccount(String userId) {
        if (userId == null || userId.isBlank()) return;

        var now = LocalDateTime.now();
        var until = now.plusDays(RETENTION_DAYS);

        keyMetaMapper.update(null, new LambdaUpdateWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getUserId, userId)
                .ne(UserKeyMeta::getPurgeStatus, "purged")
                .set(UserKeyMeta::getRetentionStartedAt, now)
                .set(UserKeyMeta::getRetentionUntil, until)
                .set(UserKeyMeta::getPurgeStatus, "retaining")
                .set(UserKeyMeta::getUpdatedAt, now));

        long keyCount = keyMetaMapper.selectCount(new LambdaQueryWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getUserId, userId));
        if (keyCount == 0) {
            var meta = new UserKeyMeta();
            meta.setUserId(userId);
            meta.setPublicFinger("");
            meta.setBackupMethod("");
            meta.setBackedUp(0);
            meta.setRetentionStartedAt(now);
            meta.setRetentionUntil(until);
            meta.setPurgeStatus("retaining");
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            keyMetaMapper.insert(meta);
        }
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void purgeExpiredKeys() {
        var now = LocalDateTime.now();
        List<UserKeyMeta> expired = keyMetaMapper.selectList(new LambdaQueryWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getPurgeStatus, "retaining")
                .isNotNull(UserKeyMeta::getRetentionUntil)
                .le(UserKeyMeta::getRetentionUntil, now)
                .last("LIMIT 200"));

        for (var meta : expired) {
            purgeOne(meta, now);
        }
    }

    private void purgeOne(UserKeyMeta meta, LocalDateTime now) {
        var fingerprint = meta.getPublicFinger();
        if (fingerprint != null && !fingerprint.isBlank()) {
            syncRecordMapper.delete(new LambdaQueryWrapper<SyncRecord>()
                    .eq(SyncRecord::getUserId, meta.getUserId())
                    .eq(SyncRecord::getKeyFingerprint, fingerprint));
        } else {
            syncRecordMapper.delete(new LambdaQueryWrapper<SyncRecord>()
                    .eq(SyncRecord::getUserId, meta.getUserId()));
            healthIndicatorMapper.delete(new LambdaQueryWrapper<HealthIndicator>()
                    .eq(HealthIndicator::getUserId, meta.getUserId()));
        }

        meta.setPurgeStatus("purged");
        meta.setPurgedAt(now);
        meta.setUpdatedAt(now);
        keyMetaMapper.updateById(meta);
        log.info("已清理过期云端密文 userId={} keyFingerprint={}", meta.getUserId(), fingerprint);
    }
}
