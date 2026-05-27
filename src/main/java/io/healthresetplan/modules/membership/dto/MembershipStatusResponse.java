package io.healthresetplan.modules.membership.dto;

import java.util.List;

public class MembershipStatusResponse {

    private boolean active;
    private String planCode;
    private String planName;
    private String expiresAt;
    /** 当前有效的权益列表 */
    private List<String> features;

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
}
