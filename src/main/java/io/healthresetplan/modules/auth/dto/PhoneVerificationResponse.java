package io.healthresetplan.modules.auth.dto;

public class PhoneVerificationResponse {
    private String status;
    private TokenResponse token;
    private String registrationTicket;

    public static PhoneVerificationResponse login(TokenResponse token) {
        PhoneVerificationResponse result = new PhoneVerificationResponse();
        result.status = "login";
        result.token = token;
        return result;
    }

    public static PhoneVerificationResponse register(String registrationTicket) {
        PhoneVerificationResponse result = new PhoneVerificationResponse();
        result.status = "register";
        result.registrationTicket = registrationTicket;
        return result;
    }

    public String getStatus() { return status; }
    public TokenResponse getToken() { return token; }
    public String getRegistrationTicket() { return registrationTicket; }
}
