package io.healthresetplan.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PhoneRegisterRequest {

    @NotBlank(message = "phone is required")
    private String phone;

    @NotBlank(message = "registrationTicket is required")
    private String registrationTicket;

    private String password;

    private String nickname;

    private boolean agreedToTerms;

    @NotBlank(message = "agreementVersion is required")
    private String agreementVersion;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRegistrationTicket() { return registrationTicket; }
    public void setRegistrationTicket(String registrationTicket) { this.registrationTicket = registrationTicket; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public boolean isAgreedToTerms() { return agreedToTerms; }
    public void setAgreedToTerms(boolean agreedToTerms) { this.agreedToTerms = agreedToTerms; }

    public String getAgreementVersion() { return agreementVersion; }
    public void setAgreementVersion(String agreementVersion) { this.agreementVersion = agreementVersion; }
}
