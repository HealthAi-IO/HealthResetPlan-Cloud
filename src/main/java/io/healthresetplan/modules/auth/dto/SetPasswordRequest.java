package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetPasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64 位")
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
