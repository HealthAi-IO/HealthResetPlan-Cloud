package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PhonePasswordLoginRequest {
    @NotBlank(message = "phone is required")
    private String phone;
    @NotBlank(message = "password is required")
    private String password;
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
