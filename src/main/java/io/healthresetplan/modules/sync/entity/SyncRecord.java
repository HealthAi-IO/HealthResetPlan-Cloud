package io.healthresetplan.modules.sync.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("sync_record")
public class SyncRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String tableName;
    private String clientId;
    private String payloadCipher;
    private String payloadIv;
    private String payloadTag;
    private String alg;
    private String metaJson;
    private String deviceId;
    private LocalDateTime clientUpdatedAt;
    private LocalDateTime serverUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getPayloadCipher() { return payloadCipher; }
    public void setPayloadCipher(String payloadCipher) { this.payloadCipher = payloadCipher; }

    public String getPayloadIv() { return payloadIv; }
    public void setPayloadIv(String payloadIv) { this.payloadIv = payloadIv; }

    public String getPayloadTag() { return payloadTag; }
    public void setPayloadTag(String payloadTag) { this.payloadTag = payloadTag; }

    public String getAlg() { return alg; }
    public void setAlg(String alg) { this.alg = alg; }

    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public LocalDateTime getClientUpdatedAt() { return clientUpdatedAt; }
    public void setClientUpdatedAt(LocalDateTime clientUpdatedAt) { this.clientUpdatedAt = clientUpdatedAt; }

    public LocalDateTime getServerUpdatedAt() { return serverUpdatedAt; }
    public void setServerUpdatedAt(LocalDateTime serverUpdatedAt) { this.serverUpdatedAt = serverUpdatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
