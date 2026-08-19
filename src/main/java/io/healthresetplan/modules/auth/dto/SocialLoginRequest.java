package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SocialLoginRequest {
    @NotBlank
    private String code;

    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
}
