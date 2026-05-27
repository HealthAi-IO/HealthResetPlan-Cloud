package io.healthresetplan.modules.report.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 保存已确认的体检报告请求体。
 * 所有内容字段均由客户端在本地加密后传入，服务端原样存储。
 */
public class ReportSaveRequest {

    @NotBlank(message = "clientId 不能为空")
    private String clientId;

    /** ISO-8601 格式报告日期，如 2024-01-15T00:00:00，可为 null */
    private String reportTime;

    private String deviceId;
    private String clientUpdatedAt;

    // ── 图像（可选，客户端上传加密图像到 OSS 后填入）──────────────
    private String imageOssKey;
    private String imageWrappedDek;
    private String imageDekIv;
    private String imageDekTag;

    // ── OCR 原始文本（客户端加密）────────────────────────────────
    private String ocrTextCipher;
    private String ocrTextIv;
    private String ocrTextTag;

    // ── 结构化指标 JSON（客户端加密）─────────────────────────────
    private String structuredCipher;
    private String structuredIv;
    private String structuredTag;

    // ── 摘要（客户端加密）────────────────────────────────────────
    private String summaryCipher;
    private String summaryIv;
    private String summaryTag;

    private String alg = "aes-256-gcm:v1";

    // ── getters / setters ──────────────────────────────────

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getClientUpdatedAt() { return clientUpdatedAt; }
    public void setClientUpdatedAt(String clientUpdatedAt) { this.clientUpdatedAt = clientUpdatedAt; }

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
}
