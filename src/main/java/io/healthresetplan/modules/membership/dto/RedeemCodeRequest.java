package io.healthresetplan.modules.membership.dto;

import jakarta.validation.constraints.NotBlank;

public class RedeemCodeRequest {

    @NotBlank
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
