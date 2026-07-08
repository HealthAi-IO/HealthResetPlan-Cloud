package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SmsLoginCodeRequest {

    @NotBlank(message = "phone is required")
    private String phone;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
