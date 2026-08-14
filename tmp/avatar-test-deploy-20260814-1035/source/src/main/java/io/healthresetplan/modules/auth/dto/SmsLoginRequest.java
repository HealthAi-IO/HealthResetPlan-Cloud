package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SmsLoginRequest {

    @NotBlank(message = "phone is required")
    private String phone;

    @NotBlank(message = "code is required")
    private String code;

    private String nickname;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
