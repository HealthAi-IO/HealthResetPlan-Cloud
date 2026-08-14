package io.healthresetplan.modules.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminRefreshRequest {

    @NotBlank(message = "refresh token 不能为空")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}

