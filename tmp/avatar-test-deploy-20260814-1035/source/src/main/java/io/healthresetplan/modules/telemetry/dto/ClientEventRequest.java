package io.healthresetplan.modules.telemetry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClientEventRequest {

    @Pattern(regexp = "^(android|ios|windows|macos|web|wechat)$", message = "platform 不合法")
    private String platform;

    @Size(max = 32)
    private String appVersion;

    @Size(max = 32)
    private String channel;

    @NotBlank
    @Pattern(regexp = "^(app_open|home_view|plan_view|clock_view|indicator_view|sync_view|ai_chat|plan_generated|clock_recorded|indicator_recorded|sync_success|sync_failure)$", message = "eventType 不合法")
    private String eventType;

    @Size(max = 128)
    private String deviceId;

    @Size(max = 64)
    private String traceId;

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
