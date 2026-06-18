package io.healthresetplan.modules.admin.auth.dto;

import jakarta.validation.constraints.Pattern;

public class TotpDisableRequest {

    @Pattern(regexp = "^\\d{6}$", message = "动态验证码必须为 6 位数字")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}

