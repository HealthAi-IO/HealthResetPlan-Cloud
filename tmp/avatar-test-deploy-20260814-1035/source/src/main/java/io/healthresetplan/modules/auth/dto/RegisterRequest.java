package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    /** phone 或 email */
    @NotBlank(message = "登录类型不能为空")
    @Pattern(regexp = "^(phone|email)$", message = "credType 只支持 phone 或 email")
    private String credType;

    /** 手机号或邮箱明文（服务端 hash 后存储） */
    @NotBlank(message = "账号不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64 位")
    private String password;

    /** 昵称（可选），默认 "健康用户" */
    private String nickname;

    public String getCredType() { return credType; }
    public void setCredType(String credType) { this.credType = credType; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
