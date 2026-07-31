package io.healthresetplan.modules.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("health_content_read")
public class HealthContentRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long contentId;
    private LocalDateTime firstReadAt;
    private LocalDateTime lastReadAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }
    public LocalDateTime getFirstReadAt() { return firstReadAt; }
    public void setFirstReadAt(LocalDateTime firstReadAt) { this.firstReadAt = firstReadAt; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
}
