package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PasswordResetCodeRequest {

    @NotBlank(message = "登录类型不能为空")
    @Pattern(regexp = "^(phone|email)$", message = "credType 只支持 phone 或 email")
    private String credType;

    @NotBlank(message = "账号不能为空")
    private String identifier;

    public String getCredType() { return credType; }
    public void setCredType(String credType) { this.credType = credType; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
}
