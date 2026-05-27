package io.healthresetplan.modules.report.dto;

import java.util.List;

/** 视觉大模型解析体检报告的结果（明文，由客户端加密后再保存）。 */
public class AnalyzeResponse {

    private String reportDate;
    private List<Indicator> indicators;
    private String summary;
    private String rawText;
    /** 本次分析使用的 AI provider */
    private String provider;
    /** 本次分析使用的模型 */
    private String model;

    public static class Indicator {
        /** 血糖 / 血脂 / 血压 / 肝功能 / 肾功能 / 血常规 / 甲状腺 / 尿常规 / 其他 */
        private String category;
        private String name;
        private String value;
        private String unit;
        private String referenceRange;
        /** normal / high / low / unknown */
        private String status;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getReferenceRange() { return referenceRange; }
        public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }

    public List<Indicator> getIndicators() { return indicators; }
    public void setIndicators(List<Indicator> indicators) { this.indicators = indicators; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
