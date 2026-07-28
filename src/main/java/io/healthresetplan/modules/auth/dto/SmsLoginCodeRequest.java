package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SmsLoginCodeRequest {

    @NotBlank(message = "phone is required")
    private String phone;
    @NotBlank(message = "captchaTicket is required")
    private String captchaTicket;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCaptchaTicket() { return captchaTicket; }
    public void setCaptchaTicket(String captchaTicket) { this.captchaTicket = captchaTicket; }
}
