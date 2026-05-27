package io.healthresetplan.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/** 对应 user_credential 表，存储手机/邮箱 hash 和密码 hash。 */
@TableName("user_credential")
public class UserCredential {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    /** phone / email / wechat */
    private String credType;

    /** 手机号或邮箱的 SHA-256 hex，不存明文 */
    private String identifierHash;

    /** BCrypt 密码 hash；OAuth 场景存 openid */
    private String secretHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic(value = "NULL", delval = "NOW(3)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime deletedAt;

    @Version
    private Long version;

    // ── getters / setters ──────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCredType() { return credType; }
    public void setCredType(String credType) { this.credType = credType; }

    public String getIdentifierHash() { return identifierHash; }
    public void setIdentifierHash(String identifierHash) { this.identifierHash = identifierHash; }

    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
