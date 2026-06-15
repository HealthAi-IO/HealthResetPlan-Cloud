package io.healthresetplan.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("user_key_meta")
public class UserKeyMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String publicFinger;
    private String backupMethod;
    private Integer backedUp;
    private LocalDateTime backedUpAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime retentionStartedAt;
    private LocalDateTime retentionUntil;
    private String purgeStatus;
    private LocalDateTime purgedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPublicFinger() { return publicFinger; }
    public void setPublicFinger(String publicFinger) { this.publicFinger = publicFinger; }

    public String getBackupMethod() { return backupMethod; }
    public void setBackupMethod(String backupMethod) { this.backupMethod = backupMethod; }

    public Integer getBackedUp() { return backedUp; }
    public void setBackedUp(Integer backedUp) { this.backedUp = backedUp; }

    public LocalDateTime getBackedUpAt() { return backedUpAt; }
    public void setBackedUpAt(LocalDateTime backedUpAt) { this.backedUpAt = backedUpAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public LocalDateTime getRetentionStartedAt() { return retentionStartedAt; }
    public void setRetentionStartedAt(LocalDateTime retentionStartedAt) { this.retentionStartedAt = retentionStartedAt; }

    public LocalDateTime getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(LocalDateTime retentionUntil) { this.retentionUntil = retentionUntil; }

    public String getPurgeStatus() { return purgeStatus; }
    public void setPurgeStatus(String purgeStatus) { this.purgeStatus = purgeStatus; }

    public LocalDateTime getPurgedAt() { return purgedAt; }
    public void setPurgedAt(LocalDateTime purgedAt) { this.purgedAt = purgedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
