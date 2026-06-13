package io.healthresetplan.modules.report.dto;

import java.util.List;

public class AnalyzeResponse {

    private String reportDate;
    private List<Indicator> indicators;
    private String summary;
    private String rawText;
    private String provider;
    private String model;

    public static class Indicator {
        private String category;
        private String name;
        private String value;
        private String unit;
        private String referenceRange;
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
