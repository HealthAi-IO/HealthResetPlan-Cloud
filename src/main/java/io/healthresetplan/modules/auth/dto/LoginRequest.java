package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LoginRequest {

    @NotBlank(message = "登录类型不能为空")
    @Pattern(regexp = "^(phone|email)$", message = "credType 只支持 phone 或 email")
    private String credType;

    @NotBlank(message = "账号不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getCredType() { return credType; }
    public void setCredType(String credType) { this.credType = credType; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
