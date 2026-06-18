package io.healthresetplan.modules.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TotpEnableRequest {

    @NotBlank(message = "TOTP secret 不能为空")
    private String secret;

    @Pattern(regexp = "^\\d{6}$", message = "动态验证码必须为 6 位数字")
    private String code;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}

