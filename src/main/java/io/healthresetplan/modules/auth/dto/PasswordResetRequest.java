package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetRequest {

    @NotBlank(message = "登录类型不能为空")
    @Pattern(regexp = "^(phone|email)$", message = "credType 只支持 phone 或 email")
    private String credType;

    @NotBlank(message = "账号不能为空")
    private String identifier;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64 位")
    private String newPassword;

    public String getCredType() { return credType; }
    public void setCredType(String credType) { this.credType = credType; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
