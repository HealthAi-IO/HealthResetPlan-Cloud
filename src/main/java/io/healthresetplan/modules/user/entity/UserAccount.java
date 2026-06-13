package io.healthresetplan.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/** 对应 user_account 表。 */
@TableName("user_account")
public class UserAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    /** 用户可自定义的展示编号 */
    private String customId;
    private String phoneTail;
    private String nickname;
    private String avatarUrl;

    /** 1=正常 0=禁用 -1=注销 */
    private Integer status;

    /** user=普通用户 admin=后台管理员 */
    private String roleCode;

    private Integer hasCloudSync;

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

    public String getCustomId() { return customId; }
    public void setCustomId(String customId) { this.customId = customId; }

    public String getPhoneTail() { return phoneTail; }
    public void setPhoneTail(String phoneTail) { this.phoneTail = phoneTail; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public Integer getHasCloudSync() { return hasCloudSync; }
    public void setHasCloudSync(Integer hasCloudSync) { this.hasCloudSync = hasCloudSync; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
