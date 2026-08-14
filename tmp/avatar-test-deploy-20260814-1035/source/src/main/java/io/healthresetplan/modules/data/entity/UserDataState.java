package io.healthresetplan.modules.data.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_data_state")
public class UserDataState {
    @TableId
    private String userId;
    private String payloadCipher;
    private String payloadNonce;
    private Integer keyVersion;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPayloadCipher() { return payloadCipher; }
    public void setPayloadCipher(String payloadCipher) { this.payloadCipher = payloadCipher; }
    public String getPayloadNonce() { return payloadNonce; }
    public void setPayloadNonce(String payloadNonce) { this.payloadNonce = payloadNonce; }
    public Integer getKeyVersion() { return keyVersion; }
    public void setKeyVersion(Integer keyVersion) { this.keyVersion = keyVersion; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
