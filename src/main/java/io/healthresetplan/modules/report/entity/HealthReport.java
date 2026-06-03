package io.healthresetplan.modules.report.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/** 对应 health_report 表，所有内容字段均为客户端客户端加密。 */
@TableName("health_report")
public class HealthReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String clientId;

    /** 客户端加密图像的 OSS Key */
    private String imageOssKey;
    /** 图像 DEK（UMK 包裹） */
    private String imageWrappedDek;
    private String imageDekIv;
    private String imageDekTag;

    /** OCR 原始文本（客户端加密） */
    private String ocrTextCipher;
    private String ocrTextIv;
    private String ocrTextTag;

    /** 结构化指标 JSON（客户端加密） */
    private String structuredCipher;
    private String structuredIv;
    private String structuredTag;

    /** 摘要（客户端加密） */
    private String summaryCipher;
    private String summaryIv;
    private String summaryTag;

    private String alg;
    private LocalDateTime reportTime;
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

    // ── getters / setters ──────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getImageOssKey() { return imageOssKey; }
    public void setImageOssKey(String imageOssKey) { this.imageOssKey = imageOssKey; }

    public String getImageWrappedDek() { return imageWrappedDek; }
    public void setImageWrappedDek(String imageWrappedDek) { this.imageWrappedDek = imageWrappedDek; }

    public String getImageDekIv() { return imageDekIv; }
    public void setImageDekIv(String imageDekIv) { this.imageDekIv = imageDekIv; }

    public String getImageDekTag() { return imageDekTag; }
    public void setImageDekTag(String imageDekTag) { this.imageDekTag = imageDekTag; }

    public String getOcrTextCipher() { return ocrTextCipher; }
    public void setOcrTextCipher(String ocrTextCipher) { this.ocrTextCipher = ocrTextCipher; }

    public String getOcrTextIv() { return ocrTextIv; }
    public void setOcrTextIv(String ocrTextIv) { this.ocrTextIv = ocrTextIv; }

    public String getOcrTextTag() { return ocrTextTag; }
    public void setOcrTextTag(String ocrTextTag) { this.ocrTextTag = ocrTextTag; }

    public String getStructuredCipher() { return structuredCipher; }
    public void setStructuredCipher(String structuredCipher) { this.structuredCipher = structuredCipher; }

    public String getStructuredIv() { return structuredIv; }
    public void setStructuredIv(String structuredIv) { this.structuredIv = structuredIv; }

    public String getStructuredTag() { return structuredTag; }
    public void setStructuredTag(String structuredTag) { this.structuredTag = structuredTag; }

    public String getSummaryCipher() { return summaryCipher; }
    public void setSummaryCipher(String summaryCipher) { this.summaryCipher = summaryCipher; }

    public String getSummaryIv() { return summaryIv; }
    public void setSummaryIv(String summaryIv) { this.summaryIv = summaryIv; }

    public String getSummaryTag() { return summaryTag; }
    public void setSummaryTag(String summaryTag) { this.summaryTag = summaryTag; }

    public String getAlg() { return alg; }
    public void setAlg(String alg) { this.alg = alg; }

    public LocalDateTime getReportTime() { return reportTime; }
    public void setReportTime(LocalDateTime reportTime) { this.reportTime = reportTime; }

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
