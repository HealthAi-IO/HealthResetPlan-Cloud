package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SocialPhoneVerifyRequest {
    @NotBlank private String ticket;
    @NotBlank private String phone;
    @NotBlank private String code;
    private boolean syncProfile;
    private String agreementVersion = "2026-08-19";

    public String getTicket() { return ticket; }
    public void setTicket(String value) { ticket = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public boolean isSyncProfile() { return syncProfile; }
    public void setSyncProfile(boolean value) { syncProfile = value; }
    public String getAgreementVersion() { return agreementVersion; }
    public void setAgreementVersion(String value) { agreementVersion = value; }
}
