package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class AccountPasswordLoginRequest {

    @NotBlank(message = "accountName is required")
    private String accountName;

    @NotBlank(message = "password is required")
    private String password;

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
