package io.healthresetplan.modules.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    private boolean enabled = false;
    private boolean debugCodeEnabled = true;
    private String provider = "jdcloud";
    private String regionId = "cn-north-1";
    private int codeTtlSeconds = 600;
    private int resendIntervalSeconds = 60;
    private int maxPerPhonePerHour = 5;
    private int maxPerPhonePerDay = 20;
    private Jdcloud jdcloud = new Jdcloud();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDebugCodeEnabled() { return debugCodeEnabled; }
    public void setDebugCodeEnabled(boolean debugCodeEnabled) { this.debugCodeEnabled = debugCodeEnabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public int getCodeTtlSeconds() { return codeTtlSeconds; }
    public void setCodeTtlSeconds(int codeTtlSeconds) { this.codeTtlSeconds = codeTtlSeconds; }

    public int getResendIntervalSeconds() { return resendIntervalSeconds; }
    public void setResendIntervalSeconds(int resendIntervalSeconds) {
        this.resendIntervalSeconds = resendIntervalSeconds;
    }

    public int getMaxPerPhonePerHour() { return maxPerPhonePerHour; }
    public void setMaxPerPhonePerHour(int maxPerPhonePerHour) { this.maxPerPhonePerHour = maxPerPhonePerHour; }

    public int getMaxPerPhonePerDay() { return maxPerPhonePerDay; }
    public void setMaxPerPhonePerDay(int maxPerPhonePerDay) { this.maxPerPhonePerDay = maxPerPhonePerDay; }

    public Jdcloud getJdcloud() { return jdcloud; }
    public void setJdcloud(Jdcloud jdcloud) { this.jdcloud = jdcloud; }

    public boolean isJdcloudReady() {
        return enabled
                && "jdcloud".equalsIgnoreCase(provider)
                && hasText(jdcloud.accessKeyId)
                && hasText(jdcloud.secretAccessKey)
                && hasText(jdcloud.signId)
                && hasText(jdcloud.templateId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class Jdcloud {
        private String accessKeyId;
        private String secretAccessKey;
        private String signId;
        private String templateId;
        private String endpoint = "sms.jdcloud-api.com";
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 5000;

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

        public String getSecretAccessKey() { return secretAccessKey; }
        public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

        public String getSignId() { return signId; }
        public void setSignId(String signId) { this.signId = signId; }

        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }
}
