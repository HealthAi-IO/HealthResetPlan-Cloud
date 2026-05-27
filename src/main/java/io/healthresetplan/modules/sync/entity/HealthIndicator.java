package io.healthresetplan.modules.sync.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/** 对应 health_indicator 表；payload 字段均为客户端 AES-256-GCM 加密，服务端不解密。 */
@TableName("health_indicator")
public class HealthIndicator {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String clientId;
    private String type;
    private String payloadCipher;
    private String payloadIv;
    private String payloadTag;
    private String alg;
    private String source;
    private LocalDateTime measuredAt;
    private String deviceId;
    private LocalDateTime clientUpdatedAt;
    private LocalDateTime serverUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic(value = "NULL", delval = "NOW(3)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime deletedAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayloadCipher() { return payloadCipher; }
    public void setPayloadCipher(String payloadCipher) { this.payloadCipher = payloadCipher; }

    public String getPayloadIv() { return payloadIv; }
    public void setPayloadIv(String payloadIv) { this.payloadIv = payloadIv; }

    public String getPayloadTag() { return payloadTag; }
    public void setPayloadTag(String payloadTag) { this.payloadTag = payloadTag; }

    public String getAlg() { return alg; }
    public void setAlg(String alg) { this.alg = alg; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }

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
